/**
 * Copyright (C) 2014 Charles Foster
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.cfoster.sparrow;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.EventListener;
import java.util.LinkedList;
import java.util.logging.Level;

public class HttpResponseParser implements Runnable, EventListener {
  private static final byte CR = (byte)0x0D;
  private static final byte LF = (byte)0x0A;

  private static final int CONTEXT_HTTP_HEADERS = 1;
  private static final int CONTEXT_HTTP_BODY = 2;

  private static final byte[][] CONTENT_LENGTH = byteArrays("Content-Length");
  private static final byte[][] CONNECTION = byteArrays("Connection");
  private static final byte[][] KEEP_ALIVE = byteArrays("Keep-Alive");
  private static final byte[][] WWW_AUTH = byteArrays("WWW-Authenticate");
  private static final byte[][] TRANSFER_ENC = byteArrays("Transfer-Encoding");

  /** temporary buffer for containing single HTTP Header lines **/
  private byte[] headerBuffer = new byte[1024];
  private int headerBufferIndex = 0;

  public static byte HTTP_VERSION_10 = (byte)'0';
  public static byte HTTP_VERSION_11 = (byte)'1';

  private static final byte SEPARATOR = (byte)':';

  private boolean connectionAlive = true;

  private Thread processingThread = null;

  private static final int UNSET = -1;

  /** Content-Length value received from Server **/
  long contentLength = UNSET;

  /** Is Session in Connection: Keep-Alive mode
   * -1 = Server not given a value.
   * 0  = Server has given "Connection: close".
   * 1  = Server has given "Connection: keep-alive".
   **/
  int connectionKeepAlive = UNSET;

  /**
   * When using Connection: Keep-Alive mode, the server may send the Header
   * Keep-Alive: timeout=5 for instance
   * -1 = Server not given a value
   * 5 = Server has given Keep-Alive: timeout=5
   * 10 = Server has given Keep-Alive: timeout=10
   */
  long keepAliveTimeout = UNSET;

  /**
   * Does the response contain the header - Transfer-Encoding: chunked
   */
  boolean transferEncodingChunked = false;

  long contentReceived = 0;

  /** Authentication Header received from Server **/
  private byte[] authenticationHeader = null;

  private byte httpVersion = UNSET;
  private int httpStatusCode = UNSET;

  private int hbScanIndex = 1;
  private int hbLastFeed = 0;

  private int context = CONTEXT_HTTP_HEADERS;

  // NOTE:
  // It has been PROVEN that transferring data from native ByteBuffer to another
  // native Byte Buffer does not have any performance benefit
  //
  // transfer from buffer1 to byte[] took 17355 ms
  // transfer from buffer1 to buffer2 took 17328 ms
  // transfer from byte[] to byte[] took 17067 ms
  //
  // There may be performance benefit in reading from a physical source into
  // a native ByteBuffer, e.g. a Socket or a File. But after that, may as well
  // just use simple byte[] buffers.

  /** Whether or not ANY event will be passed to the End Handler **/
  private boolean endHandlerReceiveEvents = true;

  /** HttpResponseHandler which can receive events **/
  private HttpResponseHandler endHandler = null;

  /** The original SendRequest which was used to send to the server **/
  private SendRequest sendRequest = null;

  private final HttpClient httpClient;
  private final SocketChannel socketChannel;

  /**
   * Data that has been written by the NIO Selector and has yet to be picked
   * up by the HttpResponseParser thread and processed.
   *
   * This represents an Unsafe address (a pointer) to a region of memory.
   */
  private long bufferAddress;

  /** current cursor position on the direct region of memory. **/
  private int bufferPosition = 0;

  /** Total size / maximum capacity of the direct region of memory. **/
  private final int bufferSize = 1024 * 1024 * 4; // make this configurable

  private final void allocBuffer() {

    if(HttpClient.log.isLoggable(Level.INFO))
      HttpClient.info("alloc_buffer", this, bufferSize);

    bufferAddress = unsafe.allocateMemory(bufferSize);
  }

  private final void deallocBuffer() {
    if(HttpClient.log.isLoggable(Level.INFO))
      HttpClient.info("dealloc_buffer", this, bufferSize);

    unsafe.freeMemory(bufferAddress);
  }

  Object lock = new Object();

  private void enqueueData(ByteBuffer buffer, int start, int length)
  {
    // System.out.println("trying to create new byte["+length+"]");

    // 1. Copy the memory from the DirectByteBuffer to a Direct Region of
    //    Memory we have Allocated with Unsafe.copyMemory.
    //
    // 2. If we realise that our Local Buffer Queue can't accept any more data
    //    then we need to inform NioClient that the Channel needs to stop
    //    accepting data for a while

    synchronized(lock)
    {
      HttpResponseParser.copy(
        address(buffer), start, bufferAddress, bufferPosition, length
      );

      bufferPosition += length;

      if(bufferPosition + httpClient.getDataInductionBufferSize() >= bufferSize)
      {
        acceptingDataEvent(endHandler, false);
      }

      lock.notify();
    }
/**
    byte[] javaBuffer = null;
    try {
      javaBuffer = new byte[length];
    } catch(OutOfMemoryError e) {
      throw e;
    }

    buffer.get(javaBuffer, start, length);

    synchronized(inputQueueBackLog)
    {
      inputQueueBackLog.add(javaBuffer);

      if(inputQueueBackLog.size() > 50) {
        acceptingDataEvent(endHandler, false);
      }


      inputQueueBackLog.notify();
    }
**/
  }

  HttpResponseParser(
    HttpClient httpClient,
    SocketChannel socketChannel)
  {
    HttpClient.log.info("new "+this);

    if(httpClient == null)
      throw new RuntimeException(
        HttpClient.log.getResourceBundle().getString("HttpResponseParser_inv1")
      );

    if(socketChannel == null)
      throw new RuntimeException(
        HttpClient.log.getResourceBundle().getString("HttpResponseParser_inv2")
      );

    this.httpClient = httpClient;
    this.socketChannel = socketChannel;
  }

  /**
   * Sets the Thread which is using this HttpResponseParser, i.e. which
   * Thread has invoked the run() method.
   */
  private void setThread(Thread thread)
  {
    if(processingThread != null && processingThread != thread)
    {
      if(HttpClient.log.isLoggable(Level.WARNING))
        HttpClient.log.log(Level.WARNING,
          "hrp_newthread", new Object[]{processingThread, thread});
      processingThread = thread;
    }
  }

  public void setHttpResponseHandler(HttpResponseHandler endHandler)
  {
    if(HttpClient.log.isLoggable(Level.INFO))
      HttpClient.info("setHttpResponseHandler_inv", this, endHandler);

    if(endHandler != null)
      allocBuffer();

    this.endHandler = endHandler;

    if(hasEndHandler())
      endHandler.setAcceptingDataListener(this);
  }

  private boolean hasEndHandler() {
    return endHandler != null;
  }

  public HttpResponseHandler getHttpResponseHandler()
  {
    return endHandler;
  }

  private InetSocketAddress inetSocketAddress=null;

  public void setSendRequest(SendRequest request)
  {
    if(HttpClient.log.isLoggable(Level.INFO))
      HttpClient.info("setHttpRequest_inv", this, request);

    this.sendRequest = request;

    if(inetSocketAddress == null && request != null)
      inetSocketAddress = request.address;
  }

  byte[] jbuffer = new byte[1024 * 1024 * 10]; // [TODO]: must be configurable.

  int jbufferSize=0;
  public void run()
  {
    setThread(Thread.currentThread());

    while(true)
    {
      if (HttpClient.log.isLoggable(Level.INFO))
        HttpClient.info("hrp_run_entered");

      // byte[] jbuffer = null;

      synchronized(lock)
      {
        while(connectionAlive && bufferPosition == 0)
        {
          try
          {
            lock.wait();
          }
          catch(InterruptedException e) {
            HttpClient.log.throwing(this.getClass().getName(), "run", e);
          }
        }

        // flush it
        HttpResponseParser.copy(bufferAddress, 0, jbuffer, 0, bufferPosition);
        jbufferSize=bufferPosition;
        bufferPosition = 0;

        // accept data
        acceptingDataEvent(endHandler, true);
      }

      if(jbuffer != null)
        handleResponse(jbuffer, 0, jbufferSize);

      if(!connectionAlive) {
        break;
      }
    }

    if(hasEndHandler())
      completedParsingEvent();
  }

  /**
   * Method is invoked by the NIO Selector Thread, so must be lightning speed.
   */
  public void handleData(ByteBuffer rsp, int start, int length)
  {
    if(HttpClient.log.isLoggable(Level.INFO))
      HttpClient.info("hrp_handle_data", this, start, length, getContext());

    if(!hasEndHandler()) {
      throw new RuntimeException(
        HttpClient.log.getResourceBundle().getString("hrh_not_set")
      );
    }

    enqueueData(rsp, start, length);
  }

  /**
   * Invoked if the Server cleanly closed the connection.
   */
  public void handleConnectionClosed()
  {
    connectionAlive = false;

    /**
    synchronized(inputQueueBackLog) {
      inputQueueBackLog.notify();
    } **/

    synchronized(lock) {
      lock.notify();
    }

  }

  public boolean handleResponse(byte[] rsp, int start, int length)
  {
    if(HttpClient.log.isLoggable(Level.INFO))
      HttpClient.info("hrh_handleResponse", start, length, hasEndHandler());

    switch(getContext())
    {
      case CONTEXT_HTTP_HEADERS:
        return headerResponse(rsp, start, length);
      case CONTEXT_HTTP_BODY:
        return bodyResponse(rsp, start, length);
      default:
        throw new RuntimeException(
          HttpClient.log.getResourceBundle().getString("unknown_state")
        );
    }
  }

  /**
   * Most of the content that goes through
   * here will be HTTP Headers, but it may
   * Contain part of the HTTP Response
  **/
  private boolean headerResponse(byte[] b, int start, int length)
  {
    if(HttpClient.log.isLoggable(Level.INFO))
      HttpClient.info("hrh_headerResponse_inv", start, length);

    appendHeaderBuffer(b, start, length);
    return scanForLineFeeds();
  }

  private void appendHeaderBuffer(byte[] b, int start, int length)
  {
    while(length + headerBufferIndex >= headerBuffer.length)
    {
      byte[] bx = new byte[headerBuffer.length * 2];
      System.arraycopy(headerBuffer, 0, bx, 0, headerBufferIndex);
      headerBuffer=bx;
      bx=null;
    }

    System.arraycopy(b, start, headerBuffer, headerBufferIndex, length);
    headerBufferIndex += length;
  }

  private boolean scanForLineFeeds()
  {
    if(HttpClient.log.isLoggable(Level.FINE))
      HttpClient.info("hrh_scanForLineFeeds_inv", getContext());

    boolean retValue = false;
    for(hbScanIndex=Math.max(0,hbScanIndex-1);
        hbScanIndex<headerBufferIndex-1;
        hbScanIndex++)
    {
      if(headerBuffer[hbScanIndex] == CR && headerBuffer[hbScanIndex+1] == LF)
      {
        int length = hbScanIndex - hbLastFeed;

        if(length != 0)
        {
          if(contentLength == UNSET || httpClient.getIncludeResponseHeader())
            handleHeaderLineFeed(headerBuffer, hbLastFeed, length);

          retValue = false;
          hbLastFeed = hbScanIndex + 2;
        }
        else
        {
          onResponseHeaderComplete();
          setContext(CONTEXT_HTTP_BODY);
          retValue = bodyResponse(
            headerBuffer, hbScanIndex + 2,
            headerBufferIndex - (hbScanIndex + 2)
          );

          // if(!retValue)
            // hbLastFeed = hbScanIndex + 2;
          return retValue;
        }
      }
    }

    if(HttpClient.log.isLoggable(Level.FINE))
      HttpClient.log.log(Level.FINE, "hrh_scanForLineFeeds_fin",
        new Object[] { "hrh_scanForLineFeeds_fin", this, getContext() });

    return retValue;
  }

  /**
   * Called when all headers have been received.
  **/
  public void onResponseHeaderComplete()
  {
    if(endHandlerReceiveEvents)
      endHandler.headersCompleted();
  }

  private static final boolean equals(
    final byte[][] compare, final byte[] b,
    final int start, final int length) {
    for(int n=0, i=start; i<start + length; i++, n++)
      if(!(b[i] == compare[0][n] || b[i] == compare[1][n]))
        return false;
    return true;
  }

  private void handleHeaderLineFeed(byte[] b, int start, int length)
  {
    if(HttpClient.log.isLoggable(Level.INFO))
      HttpClient.info("hrh_handleHeaderLineFeed_inv",
        b.length, start, length, new String(b, start, length));

    int end = start + length;
    for(int i=start;i<end;i++)
    {
      if(b[i] == SEPARATOR)
      {
        if(httpStatusCode == 401 && equals(WWW_AUTH, b, start, i - start))
        {
          authenticationHeader = new byte[end - (i+2)];
          System.arraycopy(b, i+2, authenticationHeader,
                           0, authenticationHeader.length);
          return;
        }

        if(contentLength == UNSET)
        {
          if(equals(CONTENT_LENGTH, b, start, i - start))
          {
            contentLength =
              HttpResponseParser.getLongFromString(b, i + 2, end - (i + 2));
          }
          else if(equals(TRANSFER_ENC, b, start, i - start))
          {
            contentLength = -2;

            byte firstChar = b[i + 2];
            if(firstChar == 'c' || firstChar == 'C') // c or C (for chunked)
            {
              transferEncodingChunked = true;
            }
          }
        }

        if(connectionKeepAlive == UNSET &&
           equals(CONNECTION, b, start, i - start))
        {
          byte firstChar = b[i + 2];
          if(firstChar == 99 || firstChar == 67)// c or C (for close)
          {
            connectionKeepAlive = 0; // Connection: close
          }
          else if(firstChar == 75 || firstChar == 107) // K or k
          {
            connectionKeepAlive = 1; // Connection: keep-alive
          }
        }

        if(keepAliveTimeout == UNSET && equals(KEEP_ALIVE, b, start, i-start))
        {
          for(int j= i + 2 ; j < end; j++)
          {
            int j8 = j+8;
            if(b[j] == 't' && b[j+7] == '=') // timeout=N
            {
              int timeoutStringLength = end - j8;

              for(int o=j8+1;o<end;o++)
                if(b[o] < 48 || b[o] > 57) {
                  timeoutStringLength = o - j8;
                  break;
                }

              keepAliveTimeout =
                HttpResponseParser.getLongFromString(
                  b, j8, timeoutStringLength);

              break;
            }
          }
        }

        if(httpClient.getIncludeResponseHeader() && endHandlerReceiveEvents)
        {
          endHandler.header(
            b, start, i - start, i + 2, end - (i+2)
          );
        }

        return;
      }
    }

    parseHttpStatusLine(b, start, length);
  }

  public static final int getIntegerFromString(byte[] c, int offset, int length)
  {
    int result = 0;
    for(int i= offset ; i < offset + length; i++ )
      result = result * 10 + (c[i] - 48);
    return result;
  }

  public static final long getLongFromString(byte[] c, int offset, int length)
  {
    long result = 0;
    for(int i= offset ; i < offset + length; i++ )
      result = result * 10 + (c[i] - 48);
    return result;
  }

  public  static final boolean isNibble(int b)
  {
    return b >= 0 && b <= 15;
  }

  public static final int hexChar2int(final byte b)
  { // produces 34 byte codes
    if(b >= 97) return b - 87;
    else if(b >= 65) return b - 55;
    else if(b <= 57) return b - 48;
    return -1;
  }


  final int READING_CHUNK_SIZE    = 1;
  final int READING_CHUNK_DATA    = 2;
  final int READING_CHUNK_TRAILER = 3;

  int state = READING_CHUNK_SIZE;
  boolean chunkExtension = false;
  int chunkSize = 0;
  int chunkRead = 0;
  boolean readAllChunks = false;

  private static final String decodeChar(byte v)
  {
    return v == CR ? "CR" : v == LF ? "LF" : new String(new byte[] { v }, 0, 1);
  }

  private void bodyChunked(byte[] b, int start, int length)
  {
    if(state == READING_CHUNK_SIZE)
    {
      for(int c = 0; c < length ; c++)
      {
        int n = start + c;

        // System.out.println("b["+n+"]=="+decodeChar(b[n])+" or "+((int)b[n]));

        int i = hexChar2int(b[n]);

        if(isNibble(i) && !chunkExtension)
        {
          chunkSize = (chunkSize << 4) | i;
        }
        else if(b[n] == 59) // ';', chunk extension stuff to ignore
        {
          chunkExtension = true;
        }
        else if(b[n] == LF) // parsed body chunk length
        {
          // System.out.println("b["+n+"] == LF (chunkSize="+chunkSize+")");

          if(chunkSize == 0) // read a CRLF after a 0
          {
            chunkSize = -1;
            // System.out.println("chunkSize been set to -1");
          }
          else if(chunkSize == -1) // read a CRLF after chunkSize=1
          {
            // we have reached the end of ALL chunks
            readAllChunks=true;
            // System.out.println("*readAllChunks="+readAllChunks);
          }
          else // chunkSize > 0
          {
            state = READING_CHUNK_DATA;
            chunkExtension = false;

            int canRead = Math.min(length-(c+1), chunkSize);

            // System.out.println("state=READING_CHUNK_DATA");
            // System.out.println("c="+c+", length="+length+", n="+n+
            // ", canRead="+canRead+", chunkSize="+chunkSize);


            if (c + 1 < length)
            {
              int _length = length - (c+1);

              // System.out.println("c="+(c+1));
              // System.out.println("_length="+(_length));

              // bodyChunked(b, n + 1, canRead);
              bodyChunked(b, n + 1, _length);
              c = length; // force for loop to stop
            }
          }
        }
      }
    }
    else if(state == READING_CHUNK_DATA)
    {

//      for(int i=0;i<length;i++)
//      {
//        int n = start + i;
//        System.out.println("b["+n+"]="+decodeChar(b[n])+" or "+b[n]);
//      }


      // System.out.println("READING_CHUNK_DATA(b["+b.length+"],"+start+","+length+")");
      // System.out.println("chunkSize="+chunkSize);
      // System.out.println("chunkRead="+chunkRead);
      int needToRead = chunkSize - chunkRead;
      int canRead = Math.min(needToRead, length);
      endHandler.bodyPart(b, start, canRead);
      chunkRead += canRead;
      contentReceived += canRead;

      if(canRead == needToRead) // read all of this chunk
      {
        state = READING_CHUNK_TRAILER;

        // System.out.println("canRead="+canRead+", needToRead="+needToRead);
        // System.out.println("start="+start+", length="+length);

        int v1 = start + needToRead;
        int v2 = length - needToRead;

        // start=203, length=4

        chunkSize=0;
        chunkRead=0;

        // System.out.println("YO");

        // System.out.println("v1=" + v1 + ", v2="+ v2);

        if(v2 > 0)
        {
          bodyChunked(b, v1, v2);
        }
      }
    }
    else if(state == READING_CHUNK_TRAILER)
    {
      // chunk terminating CR and LF are just junk that we need to bypass

      for(int i=0;i<length;i++)
      {
        int j = start + i;

        if(b[j] == LF)
        {
          state = READING_CHUNK_SIZE;

          int _a = j + 1;
          int _b = length - (i+1);

          bodyChunked(b, _a, _b);
          i = length;
        }
      }
    }
  }

  /**
   * @return whether or not the parser has seen enough data.
   */
  private boolean bodyResponse(byte[] b, int start, int length)
  {
  // System.out.println("bodyResponse(b["+b.length+"], "+start+", "+length+")");

// Very Last Byte is b[start + 53]
//    for(int i=0;i<length;i++)
//    {
//      int n = start + i;
//      String V = b[n] == CR ? "CR" : b[n] == LF ? "LF" : new String(b, n, 1);
//      System.out.println("xb["+i+"]="+V+" or "+b[n]);
//    }

    // System.out.println(new String(b, start, length));


    if(HttpClient.log.isLoggable(Level.INFO))
      HttpClient.info("hrh_bodyResponse_inv", start, length);

    // byte[] temp = new byte[length];
    // System.arraycopy(b,start,temp,0,length);

    if(endHandlerReceiveEvents)
    {
      // System.out.println("GOT SOME DATA");
      if(isTransferEncodingChunked() && !readAllChunks)
      {
        bodyChunked(b, start, length);
      }
      else
      {
        endHandler.bodyPart(b, start, length);
        contentReceived += length;
      }

      // endHandler.bodyPart(temp);
    }
    else
    {
      contentReceived += length;
    }

//    System.out.println(
//      "readAllChunks="+readAllChunks+"," +
//        "connection-close="+isConnectionClose()+","+
//       " inputQueueBackLogSize="+inputQueueBackLog.size());

    if(isConnectionClose()) // Connection: close
    {
      if(HttpClient.log.isLoggable(Level.INFO))
        HttpClient.info("conn_alive", connectionAlive);

/**
      synchronized(inputQueueBackLog)
      {
        if(inputQueueBackLog.size() == 0 && !connectionAlive)
        {
          completedParsingEvent();
        }
      }
**/


      synchronized(lock)
      {
        if(bufferPosition == 0 && !connectionAlive)
        {
          completedParsingEvent();
        }
      }

      return !connectionAlive;
    }
    else if(isTransferEncodingChunked())
    {
      // System.out.println("** isTransferEncodingChunked(),
      // readAllChunks="+readAllChunks);

      if(readAllChunks)
      {
        completedParsingEvent();
        return true;
      }
      else
      {
        return false;
      }
    }
    else if(contentReceived >= contentLength)
    {
      // System.out.println("contentReceived="+contentReceived+",
      // contentLength="+contentLength);
      completedParsingEvent();
      return true;
    }
    else
    {
      return false;
    }
  }

  public void completedParsingEvent()
  {
    if(sendRequest == null)
      return;

    // System.out.println("completedParsingEvent()");
    // new Exception().printStackTrace();
    // System.out.println();System.out.println();


    HttpClient client = httpClient;
    HttpResponseHandler handler = endHandler;
    boolean completeEvent = endHandlerReceiveEvents;
    int statusCode = httpStatusCode;
    SendRequest request = sendRequest;
    // SocketChannel socket = socketChannel;
    byte[] authHeader = authenticationHeader;
    boolean isConnectionClose = isConnectionClose();
    long _keepAliveTimeout = keepAliveTimeout;

    if(hasEndHandler() && completeEvent)
      handler.completed();

    resetState();

    // System.out.println("client.responseCompleteEvent()");

    // System.out.println("\tsocketChannel=" + socketChannel);
    // System.out.println("\tconnectionClose=" + isConnectionClose);
    // System.out.println("\trequest=" + request);

    client.responseCompleteEvent(
      socketChannel,
      isConnectionClose,
      request.address,
      _keepAliveTimeout
    );

    switch(statusCode)
    {
      case 401: // Unauthorized

        if(client.log.isLoggable(Level.INFO))
          client.log.info("401_resending");

        request.request.addFailedAuthorizationAttempt();

        client.setAuthorization(request.address, new Authorization(authHeader));
        client.send(request.request, request.address, request.handler);

      break;
    }
  }

  private void setContext(int httpContext)
  {
    context = httpContext;
  }

  private int getContext()
  {
    return context;
  }

  // Status-Line = HTTP-Version SP Status-Code SP Reason-Phrase CRLF
  // "HTTP/" 1*DIGIT "." 1*DIGIT SP 3DIGIT SP
  // HTTP/1.1 300 Reason
  private void parseHttpStatusLine(byte[] b, int start, int length)
  {
    httpVersion = b[7];
    httpStatusCode = 100 * (b[9] - 48) + 10 * (b[10] - 48) + (b[11] - 48);

    if(httpClient.log.isLoggable(Level.INFO))
      httpClient.info("rec_status_code", httpStatusCode);

    if(httpStatusCode == 401 &&
      !sendRequest.request.tooManyFailedAuthorizationAttempts())
    {
      endHandlerReceiveEvents = false;
    }

    if(endHandlerReceiveEvents && httpClient.getIncludeResponseStatusLines())
    {
      endHandler.statusLine(
        httpVersion, httpStatusCode,
        b, 13, length - 13
      );
    }
  }

  public boolean isConnectionClose() {
    return connectionKeepAlive == 0;
  }

  public boolean isTransferEncodingChunked() {
    return transferEncodingChunked;
  }

  public void resetState()
  {
    if(HttpClient.log.isLoggable(Level.INFO))
      HttpClient.info("resetState_inv", this);

    contentLength = UNSET;
    contentReceived = 0;
    setContext(CONTEXT_HTTP_HEADERS);
    // setHttpResponseHandler(null); // commented out on 2014-11-3
    hbLastFeed = 0;
    hbScanIndex = 1;
    headerBufferIndex = 0;
    httpStatusCode = UNSET;
    httpVersion = UNSET;
    endHandlerReceiveEvents = true;
    setSendRequest(null);
    // sendRequest = null;
    connectionAlive = true;
    connectionKeepAlive = UNSET;
    keepAliveTimeout = UNSET;
    transferEncodingChunked = false;

    deallocBuffer();
    // headers.clear();
  }

  private boolean isNowAcceptingData=true;

  public void acceptingDataEvent(
    HttpResponseHandler handler,
    boolean isNowAcceptingData)
  {
    if(this.isNowAcceptingData != isNowAcceptingData)
    {
      httpClient.acceptingDataEvent(
        socketChannel,
        isNowAcceptingData
      );

      this.isNowAcceptingData = isNowAcceptingData;
    }
  }

  public static final byte[][] byteArrays(final String string) {
    final byte[][] b = new byte[2][string.length()];
    for(int i=0;i<string.length();i++) {
      final char ch = string.charAt(i);
      b[0][i] = (byte)ch;
      if(Character.isUpperCase(ch))
        b[1][i] = (byte)Character.toLowerCase(ch);
      else if(Character.isLowerCase(ch))
        b[1][i] = (byte)Character.toUpperCase(ch);
    }
    return b;
  }

  public static long address(ByteBuffer direct)
  {
    try {
      Method addressMethod = direct.getClass().getDeclaredMethod("address");
      addressMethod.setAccessible(true);
      return (Long)addressMethod.invoke(direct);
    } catch(Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void copy(long srcAddr, int srcPos,
                          long destAddr, int destPos, int length) {

    unsafe.copyMemory(srcAddr + srcPos, destAddr + destPos, length);
  }

  public static void copy(long srcAddr, int srcPos,
                          byte[] dest, int destPos, int length)
  {
    copyToArray(srcAddr + srcPos, dest, arrayBaseOffset, destPos, length);
  }

  static void copyToArray(long srcAddr, Object dst, long dstBaseOffset,
                          long dstPos, long length)
  {
    long l5;
    for(long l4 = dstBaseOffset + dstPos; length > 0L; l4 += l5)
    {
      l5 = length <= 0x100000L ? length : 0x100000L;
      unsafe.copyMemory(null, srcAddr, dst, l4, l5);
      length -= l5;
      srcAddr += l5;
    }
  }

  private static final Unsafe unsafe;
  private static long arrayBaseOffset;

  public static Unsafe getUnsafe() {
    return unsafe;
  }

  static {
    try
    {
      Field f = Unsafe.class.getDeclaredField("theUnsafe");
      f.setAccessible(true);
      unsafe = (Unsafe)f.get(null);
      arrayBaseOffset = unsafe.arrayBaseOffset(byte[].class);
    } catch(Throwable e) {
      throw new RuntimeException(e);
    }

  }
}
