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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpClient implements Runnable, ThreadFactory
{
  /** The selector we'll be monitoring **/
  private Selector selector;

  private final ThreadPoolExecutor parserThreadPool;

  /**
   * The Maximum amount of information which can be downloaded from a Socket
   * in one go. Default is 700 KB. [TODO]: This should be a configurable knob.
   */
  private int DATA_INDUCTION_BUFFER_SIZE = 1024 * 100 * 7; // 700 KB

  /** The buffer into which we'll read data when it's available **/
  private final ByteBuffer readBuffer =
    ByteBuffer.allocateDirect(getDataInductionBufferSize());

  /** A list of PendingChange instances **/
  private final List pendingChanges = new LinkedList();

  /** Maps a SocketChannel to a list of ByteBuffer instances **/
  private final Map<SocketChannel, ArrayList<HttpRequest>> pendingData =
    new HashMap<SocketChannel, ArrayList<HttpRequest>>();

  /** Maps a SocketChannel to a HttpResponseParser **/
  private final Map<SocketChannel, HttpResponseParser> httpParsers =
    new HashMap<SocketChannel, HttpResponseParser>();

  /** Connection Pools for each InetSocketAddress **/
  private final Map<InetSocketAddress, ConnectionPool> connectionPools =
    new HashMap<InetSocketAddress, ConnectionPool>();

  /** Maps InetSocketAddresses to Authorization Handlers **/
  private final Map<InetSocketAddress, Authorization> authorizationMap =
    new HashMap<InetSocketAddress, Authorization>();

  /** Can HTTPResponseHandlers retrieve status line events **/
  private boolean includeResponseStatusLines = true;

  /** Can HTTPResponseHandlers retrieve response header events **/
  private boolean includeResponseHeaders = true;

  /** e.g. HTTP/1.1 or HTTP/1.0 etc **/
  private byte[] transportProtocol = STR_HTTP_11;

  private static final ResourceBundle	msgBundle =
    ResourceBundle.getBundle("msg/sparrow");

  private Logger log = null; // = Logger.getAnonymousLogger("msg/sparrow");

/**
  static {
    // Level LOG_LEVEL = Level.OFF;
    Level LOG_LEVEL = Level.FINEST;
    log.setLevel(LOG_LEVEL);
    ConsoleHandler handler = new ConsoleHandler();
    handler.setLevel(LOG_LEVEL);
    log.addHandler(handler);
    log.setUseParentHandlers(false);
  }
**/

  /**
   * for debugging purposes
   */
  public Map<SocketChannel, HttpResponseParser> httpParsers() {
    return httpParsers;
  }

  private final SendRequestManager manager = new SendRequestManager(this);

  private static final byte[][] REQUEST_METHOD_STRING = {
    { 'G','E','T' },
    { 'P','O','S','T' },
    { 'P','U','T' }
  };

  /* HTTP/1.1 */
  public static final byte[] STR_HTTP_11 = {
    72, 84, 84, 80, 47, 49, 46, 49
  };

  /* HTTP/1.0 */
  public static final byte[] STR_HTTP_10 = {
    72, 84, 84, 80, 47, 49, 46, 48
  };

  /* Content-Length */
  private static final byte[] STR_CONTENT_LENGTH = {
    67, 111, 110, 116, 101, 110, 116, 45, 76, 101, 110, 103, 116, 104
  };

  /* "Authorization: " */
  private static final byte[] STR_AUTHORIZATION = {
    65, 117, 116, 104, 111, 114, 105, 122, 97, 116, 105, 111, 110, 58, 32
  };

  /* "Cookie: " */
  private static final byte[] STR_COOKIE = {
    67, 111, 111, 107, 105, 101, 58, 32
  };

  /* "; path=/" */
  private static final byte[] STR_COOKIE_PATH = {
    59, 32, 112, 97, 116, 104, 61, 47
  };

  private static final byte[] BIN_0x0D_0x0A = { 13, 10 }; // 0x0D 0x0A

  private static final byte[] STRING_BAD_CREDENTIALS
    = "Bad user/pass credentials.".getBytes();

  private static HttpClient runningClient = null;

  public static HttpClient getInstance()
  {
    if(runningClient != null)
      return runningClient;
    else
      return runningClient=new HttpClient();
  }

  private HttpClient(int parserThreadPoolSize)
  {
    parserThreadPool =
      (ThreadPoolExecutor)Executors.newFixedThreadPool(
      parserThreadPoolSize, this);

    // FileHandler handler = new FileHandler(TEMP_DIR + "/out.log");
    // FileHandler handler = new FileHandler("C:\\crf\\misc\\tmp\\out.log");
    // FileHandler handler = new FileHandler("M:\\out.log");

    if(isLoggable(Level.INFO))
      info("http_client_init", this);

    try {
      selector = initSelector();
    }
    catch(IOException e) {
      throw new RuntimeException(e);
    }

    Thread managerThread =
      new Thread(manager, "sparrow-send-request-thread-manager");
    managerThread.setDaemon(true);
    managerThread.start();

    Thread nioThread = new Thread(this, "sparrow-nio-selector-thread");
    nioThread.setDaemon(true);
    nioThread.start();
  }

  private HttpClient()
  {
    this(64);
  }

  public void send(
    HttpRequest request,
    InetSocketAddress address,
    HttpResponseHandler handler)
  {
    // Set set = request.getParameterNames();
    // System.out.println(set);
    manager.add(new SendRequest(request, address, handler));
  }

  void sendBatch(SendRequest[] requests) throws IOException
  {
    // System.out.println("submitting "+requests.length+" requests.");

    for(int i=0;i<requests.length;i++)
      sendImpl(requests[i]);

    if(isLoggable(Level.FINE))
      log(Level.FINE, "client_send_batch", requests.length);

    selector.wakeup();
  }

  /** short cut method, for info **/
  final void info(String msg, Object... params) {
    log(Level.INFO, msg, params);
  }

  final void finer(String msg, Object... params) {
    log(Level.FINER, msg, params);
  }

  final boolean isLoggable(Level level) {
    return log != null && log.isLoggable(level);
  }

  final String format(String msg, Object... params) {
    return MessageFormat.format(msgBundle.getString(msg), params);
  }

  final void log(Level level, String msg, Object... params) {
    if(log != null)
      log.log(level, format(msg, params));
  }

  void throwing(String sourceClass, String sourceMethod, Throwable thrown) {
    if(log != null)
      log.throwing(sourceClass, sourceMethod, thrown);
  }



  private void sendImpl(SendRequest sendRequest) throws IOException
  {
    HttpRequest request = sendRequest.request;
    InetSocketAddress address = sendRequest.address;
    HttpResponseHandler handler = sendRequest.handler;

    if(isLoggable(Level.FINE))
      log(Level.FINE, "send_impl_inv1", request, address, handler);

    // Start a new connection

    SocketChannel socket = initiateConnection(address, handler);

    HttpResponseParser httpResponseParser = null;

    synchronized(httpParsers) {
      httpResponseParser = (HttpResponseParser)httpParsers.get(socket);
    }

    // System.out.println("** setSendRequest === "+sendRequest);

    httpResponseParser.setHttpResponseHandler(handler);
    httpResponseParser.setSendRequest(sendRequest);

    if(isLoggable(Level.FINE))
      log(Level.FINE, "send_impl_inv2", httpResponseParser, handler);

    // And queue the data we want written
    synchronized(pendingData)
    {
      ArrayList queue = pendingData.get(socket);
      if (queue == null) {
        queue = new ArrayList();
        if(isLoggable(Level.FINE))
          log(Level.FINE, "no_pdq4socket",
            socket.hashCode(), socket, System.identityHashCode(queue));

        pendingData.put(socket, queue);
      }

      queue.add(request);

      if(isLoggable(Level.INFO))
        info("pdq_add", request, System.identityHashCode(queue), queue.size(),
             socket.hashCode(), socket);
    }

    // Finally, wake up our selecting thread so it can make the required changes
    // selector.wakeup();
  }


  public void run()
  {
    while(true)
    {
      try
      {
        // long t1 = System.currentTimeMillis();

        // int changesMade = 0;
        // int wroteTo = 0;
        // int readFrom = 0;
        // int connectedTo = 0;
        // Process any pending changes
        synchronized(pendingChanges)
        {
          Iterator<ChangeRequest> changes = pendingChanges.iterator();

          while(changes.hasNext())
          {
            ChangeRequest change = changes.next();

            switch(change.type)
            {
              case ChangeRequest.CHANGEOPS:
                SelectionKey key = change.socket.keyFor(selector);
                key.interestOps(change.ops);
              break;
              case ChangeRequest.REGISTER:
                change.socket.register(selector, change.ops);
              break;
              case ChangeRequest.CANCEL:
                clearUpSocketResources(change.socket);
                SelectionKey selectionKey = change.socket.keyFor(selector);
                if(selectionKey != null) {
                  selectionKey.cancel();
                }
              break;
            }

            if(isLoggable(Level.INFO))
              info("proc_change_request", change, pendingChanges.size());
          }

          if(isLoggable(Level.INFO))
            info("clear_pending_changes", "Clearing", pendingChanges.size());

          pendingChanges.clear();

          if(isLoggable(Level.INFO))
            info("clear_pending_changes", "Cleared", pendingChanges.size());
        }

        // Wait for an event one of the registered channels
        // long selectStart = System.currentTimeMillis();
        selector.select();

        if(isLoggable(Level.INFO))
          info("selector_selected");

        // long selectFinish = System.currentTimeMillis();
        // int selectedKeysAmount = selector.selectedKeys().size();

        // Iterate over the set of keys for which events are available
        Iterator selectedKeys = selector.selectedKeys().iterator();

        while (selectedKeys.hasNext())
        {
          SelectionKey key = (SelectionKey)selectedKeys.next();
          selectedKeys.remove();

          if(!key.isValid())
          {
            if(isLoggable(Level.WARNING))
              log(Level.WARNING, "key_invalid", key);
            continue;
          }

          // Check what event is available and deal with it
          if (key.isConnectable()) {
            finishConnection(key);
            // connectedTo++;
          }
          else if (key.isReadable()) {
            read(key);
            // readFrom++;
          }
          else if (key.isWritable()) {
            write(key);
            // wroteTo++;
          }

        }
      }
      catch(CancelledKeyException e) {
        // this is to be expected ...
      } catch (Exception e) {
        throwing(this.getClass().getName(), "run", e);
        e.printStackTrace();
      }
    }
  }

  /**
   * When a HttpResponseParser has been notified by it's HttpResponseHandler
   * end-point that it simply can not accept any input data (AT THE MOMENT),
   * due to already being overloaded, this method is invoked with
   * isNowAcceptingData being false.
   *
   * Once the SocketParser / HttpResponseHandler have room for some more then
   * this method is invoked again, with isNowAcceptingData being true.
   */
  public void acceptingDataEvent(
    SocketChannel c,
    boolean isNowAcceptingData)
  {
    int new_ops = isNowAcceptingData ? SelectionKey.OP_READ : 0;

    ChangeRequest req = new ChangeRequest(c, ChangeRequest.CHANGEOPS, new_ops);

    synchronized(pendingChanges) {
      pendingChanges.add(req);
    }

    selector.wakeup();
  }



  private void read(SelectionKey key) throws IOException
  {
    SocketChannel socketChannel = (SocketChannel)key.channel();

    HttpResponseParser responseParser = httpParsers.get(socketChannel);

    /**
    if(!responseParser.acceptingData())
    {
      System.out.println("WASTED Cycles!!");
      return;
    }
    **/

    // Clear out our read buffer so it's ready for new data
    readBuffer.clear();

    // Attempt to read off the channel
    int numRead=0;

    try
    {
      numRead = socketChannel.read(readBuffer);

      if(isLoggable(Level.FINEST))
        log(Level.FINEST, "read_bytes", numRead);

    }
    catch (IOException e)
    {
      e.printStackTrace();
      throwing(this.getClass().getName(), "read", e);
      // The remote forcibly closed the connection, cancel
      // the selection key and close the channel.
      key.cancel();
      socketChannel.close();
      return;
    }

    if (numRead == -1)
    {
      // Remote entity shut the socket down cleanly. Do the
      // same from our end and cancel the channel.

      // System.out.println("HTTP Parsers Size = "+httpParsers.size());
      // Parser Thread Pool Active Count = "+parserThreadPool.getActiveCount()
      // System.out.println("Connection Pools Size = "+connectionPools.size());
      // System.out.println("SOCKET CLOSED due to server closing connection.");

      key.channel().close();
      key.cancel();

      responseParser.handleConnectionClosed();
      return;
    }

    // Handle the response
    handleResponse(socketChannel, readBuffer, numRead);
  }

  private void handleResponse(
    SocketChannel socketChannel,
    ByteBuffer data,
    int numRead) throws IOException
  {
    HttpResponseParser responseParser = httpParsers.get(socketChannel);
    data.flip();
    responseParser.handleData(data, 0, numRead);
  }

  public void setIncludeResponseStatusLines(boolean flag) {
    includeResponseStatusLines = flag;
  }

  public boolean getIncludeResponseStatusLines() {
    return includeResponseStatusLines;
  }

  public void setIncludeResponseHeaders(boolean flag) {
    includeResponseHeaders = flag;
  }

  public boolean getIncludeResponseHeader() {
    return includeResponseHeaders;
  }

  public void setAuthorization(InetSocketAddress addr, Authorization authObj) {
    synchronized(authorizationMap) {
      authorizationMap.put(addr, authObj);
    }
  }

  public void responseCompleteEvent(
    SocketChannel socketChannel,
    boolean isConnectionClose,
    InetSocketAddress address) {
    responseCompleteEvent(socketChannel, isConnectionClose, address, -1);
  }
  /**
   * Executed when the handler has seen enough data / finished parsing response.
  **/
  public void responseCompleteEvent(
    SocketChannel channel,
    boolean isConnectionClose,
    InetSocketAddress address,
    long keepAliveTimeout)
  {
    if(isLoggable(Level.INFO))
    {
      info("resp_comp_ev_inv1", this, channel);
      info("resp_comp_ev_inv2", channel, channel.isConnected(),
        channel.isConnectionPending(), channel.isOpen());
    }

    ConnectionPool connectionPool = connectionPools.get(address);

    if(!channel.isConnected() || isConnectionClose)
    {
      // System.out.println(
        // "socketChannel.isConnection()="+socketChannel.isConnected()+","+
        // " isConnectionClose="+isConnectionClose);
      socketDiedEvent(channel);
      connectionPool.deregisterActiveConnection(channel);
      return;
    }

    synchronized(pendingChanges) {
      pendingChanges.add(
        new ChangeRequest(channel, ChangeRequest.CHANGEOPS,SelectionKey.OP_READ)
      );
    }

    connectionPool.pool(channel, keepAliveTimeout);

    if(isLoggable(Level.FINE)) {
      log(Level.FINE, "active_conn_count",
        connectionPool.getActiveConnectionsCount());
    }

    selector.wakeup();

    if(isLoggable(Level.INFO))
      info("channel_back2pool", channel, address);
  }

  public void socketDiedEvent(SocketChannel channel)
  {
    if(isLoggable(Level.INFO))
      info("socket_died", channel.hashCode(), channel);

    synchronized(pendingChanges) {
      pendingChanges.add(new ChangeRequest(channel, ChangeRequest.CANCEL, 0));
    }

    selector.wakeup();
  }

  private void clearUpSocketResources(SocketChannel socketChannel)
  {
    ArrayList<HttpRequest> list = pendingData.get(socketChannel);

    if(list != null)
      list.clear();

    pendingData.remove(socketChannel);

    HttpResponseParser parser = httpParsers.get(socketChannel);
    parser.handleConnectionClosed();

    // parser.notify();
    // pool http parser or something ...

    httpParsers.remove(socketChannel);
  }

  /**
   * The server has not accepted user credentials more than X times.
   */
  public void handleBadAuthentication(SendRequest sendRequest)
  {
    HttpRequest httpRequest = sendRequest.request;

    HttpResponseHandler handler = sendRequest.handler;

    handler.statusLine(
      HttpResponseParser.HTTP_VERSION_11,
      401, STRING_BAD_CREDENTIALS, 0, STRING_BAD_CREDENTIALS.length
    );

    handler.headersCompleted();
    handler.completed();
  }

  /** Sets the HTTP based transport protocol string, e.g. HTTP/1.1 **/
  public void setTransportProtocol(byte[] transportProtocol) {
    this.transportProtocol = transportProtocol;
  }

  /** Retrieves the HTTP based transport protocol, e.g. HTTP/1.1 **/
  public byte[] getTransportProtocol() {
    return transportProtocol;
  }

  private void writeHttpRequest(
    HttpRequest request,
    SocketChannel socket) throws IOException
  {
    if(isLoggable(Level.FINER))
      finer("writeHttpRequest_inv");

    byte[] coreBuffer = new byte[1024 * 10]; // [TODO]: WASTE!!!!

    ByteBuffer messageHeader =  ByteBuffer.wrap(coreBuffer);

    if(isLoggable(Level.FINER)) {
      finer("writeHttpRequest1", messageHeader);
      finer("writeHttpRequest2", request.getMethod());
    }

    if(isLoggable(Level.INFO))
      info("writeHttpRequest3",
        new String(REQUEST_METHOD_STRING[request.getMethod()]));

    messageHeader.put(REQUEST_METHOD_STRING[request.getMethod()]);

    if(isLoggable(Level.FINER))
      finer("writeHttpRequest4",
        new String(REQUEST_METHOD_STRING[request.getMethod()]));

    messageHeader.put(new byte[] { (byte)' ' });

    messageHeader.put(request.getUri()); // GET / HTTP/1.1

    if((request.getMethod() == HttpRequest.METHOD_GET ||
       request.getMethod() == HttpRequest.METHOD_PUT) &&
       request.hasParameters())
    {
      messageHeader.put((byte)'?');
      messageHeader.flip();
      forceWrite(socket, messageHeader);
      messageHeader.clear();
      request.writeHttpParameters(socket);
    }

    messageHeader.put(new byte[] { (byte)' ' });
    messageHeader.put(transportProtocol);
    messageHeader.put(BIN_0x0D_0x0A);

    Iterator iterator = request.getHeaderNames().iterator();

    // Write HTTP Headers
    while(iterator.hasNext())
    {
      byte[] name = (byte[])iterator.next();
      putHttpHeader(name, request.getHeader(name), messageHeader);
    }

    iterator = request.getCookieNames().iterator();

    // Write HTTP Cookies
    while(iterator.hasNext())
    {
      byte[] name = (byte[])iterator.next();
      putHttpHeaderCookie(name, request.getCookie(name), messageHeader);
    }

    if(request.getMethod() == HttpRequest.METHOD_POST ||
       request.getMethod() == HttpRequest.METHOD_PUT)
    {
      int count = request.getContentLength();
      putHttpHeader(STR_CONTENT_LENGTH,
        String.valueOf(count).getBytes(), messageHeader);
    }

    if(request.getUser() != null)
    {
      InetSocketAddress address =
        (InetSocketAddress)socket.socket().getRemoteSocketAddress();

      Authorization auth = null;

      synchronized(authorizationMap) {
        auth = (Authorization)authorizationMap.get(address);
      }
      if(auth != null) {
        messageHeader.put(STR_AUTHORIZATION);
        messageHeader.put(
          auth.getAuthorizationResponse(
            REQUEST_METHOD_STRING[request.getMethod()],
            request.getUri(),
            request.getUser(),
            request.getPassword()
          ));
        messageHeader.put(BIN_0x0D_0x0A);
      }
    }

    if(request.getMethod() == HttpRequest.METHOD_POST ||
      request.getMethod() == HttpRequest.METHOD_PUT)
      messageHeader.put(BIN_0x0D_0x0A);

    messageHeader.flip();

    forceWrite(socket, messageHeader);

    if(request.getMethod() == HttpRequest.METHOD_POST)
    {
      request.writeHttpParameters(socket);
    }
    else if(request.getMethod() == HttpRequest.METHOD_PUT)
    {
      byte[] chunk;

      if((chunk = request.getBodyPrefix()) != null)
        forceWrite(socket, ByteBuffer.wrap(chunk));

      forceWrite(socket, request.getBody());

      if((chunk = request.getBodySuffix()) != null)
        forceWrite(socket, ByteBuffer.wrap(chunk));
    }
    else
      forceWrite(socket, ByteBuffer.wrap(BIN_0x0D_0x0A));

    if(isLoggable(Level.INFO))
      info("wrote2socket", socket);
  }

  private static final void putHttpHeader(
    byte[] name,
    byte[] value,
    ByteBuffer buffer)
  {
    buffer.put(name);
    buffer.put(new byte[] { (byte)':' });
    buffer.put(new byte[] { (byte)' ' });
    buffer.put(value);
    buffer.put(BIN_0x0D_0x0A);
  }

  private static final void putHttpHeaderCookie(
    byte[] cookieName,
    byte[] value,
    ByteBuffer buffer)
  {
    buffer.put(STR_COOKIE); // "Cookie: "
    buffer.put(cookieName);
    buffer.put(new byte[] { (byte)'=' });
    buffer.put(value);
    buffer.put(STR_COOKIE_PATH); // "; path=/"
    buffer.put(BIN_0x0D_0x0A);
  }

  public static final void forceWrite(
    WritableByteChannel socket,
    ByteBuffer buffer) throws IOException
  {
    socket.write(buffer);

    if(buffer.remaining() > 0) {
      Thread.yield();
      forceWrite(socket, buffer);
    }
  }

  private void write(SelectionKey key) throws IOException
  {
    SocketChannel socketChannel = (SocketChannel) key.channel();

    synchronized(pendingData)
    {
      List queue = pendingData.get(socketChannel);

      // Write until there's not more data ...

      if(queue == null) {
        if(isLoggable(Level.WARNING))
          log(Level.WARNING, "write1");
        return;
      }

      if(queue.isEmpty())
      {
        if(isLoggable(Level.SEVERE))
          log(Level.SEVERE, "write2", queue, System.identityHashCode(queue));
        return;
      }

      if(isLoggable(Level.INFO))
        info("write3",
          queue.size(), System.identityHashCode(queue), socketChannel);

      while(!queue.isEmpty())
      {
        HttpRequest request = (HttpRequest)queue.remove(0);
        writeHttpRequest(request, socketChannel);
      }

      if (queue.isEmpty()) {
        // We wrote away all data, so we're no longer interested
        // in writing on this socket. Switch back to waiting for
        // data.
        key.interestOps(SelectionKey.OP_READ);
      } else {
        if(isLoggable(Level.WARNING))
          log(Level.WARNING, "queue_not_empty");
      }

    }
  }

  private void finishConnection(SelectionKey key) throws IOException
  {
    SocketChannel channel = (SocketChannel)key.channel();
    if(isLoggable(Level.INFO))
      info("finish_conn", key, channel.hashCode(), channel);

    // Finish the connection. If the connection operation failed
    // this will raise an IOException.

    try
    {
      channel.finishConnect();

      if(isLoggable(Level.INFO))
        info("conn_success", channel.hashCode(), channel);
    }
    catch (IOException e)
    {
      // Cancel the channel's registration with our selector

      if(isLoggable(Level.SEVERE))
        log(Level.SEVERE, "conn_failure", channel.hashCode(), channel);

      throwing(this.getClass().getName(), "finishConnection", e);
      key.cancel();

      HttpResponseHandler handler =
        (httpParsers.get(channel)).getHttpResponseHandler();

      handler.connectionFailed(e);
      socketDiedEvent(channel);
      return;
    }

    // Register an interest in writing on this channel
    key.interestOps(SelectionKey.OP_WRITE);
  }

  private SocketChannel initiateConnection(
    InetSocketAddress address,
    HttpResponseHandler handler) throws IOException
  {
    if(isLoggable(Level.INFO))
      info("initiateConnection_inv", address);

    SocketChannel channel = null;

    ConnectionPool connectionPool = null;

    connectionPool = connectionPools.get(address);
    if(connectionPool == null)
      connectionPools.put(address, connectionPool = new ConnectionPool(this));

    channel = connectionPool.getConnection();

    if(channel == null)
    {
      channel = initiateNewConnection(address/**, handler **/);
      connectionPool.registerActiveConnection(channel);
    }
    else
    {
      synchronized(pendingChanges) {
        pendingChanges.add(
          new ChangeRequest(
            channel, ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE));
      }
      if(isLoggable(Level.INFO))
        info("reused_conn");
    }

    return channel;
  }

  /**
   * Creates a brand new SocketChannel.
   */
  private SocketChannel initiateNewConnection(
    InetSocketAddress address/**,
    HttpResponseHandler handler **/) throws IOException
  {
    // Create a non-blocking socket channel

    if(isLoggable(Level.FINER))
      finer("initiateNewConnection_inv");

    SocketChannel socketChannel = SocketChannel.open();
    socketChannel.configureBlocking(false);

    // Kick off connection establishment
    try
    {
      socketChannel.connect(address);
    }
    catch(IllegalArgumentException e) {
      throwing(this.getClass().getName(), "initiateNewConnection", e);
    }

    if(isLoggable(Level.INFO))
      info("created_socket", socketChannel.hashCode(), socketChannel);

    // Queue a channel registration since the caller is not the
    // selecting thread. As part of the registration we'll register
    // an interest in connection events. These are raised when a channel
    // is ready to complete connection establishment.
    synchronized(pendingChanges)
    {
      pendingChanges.add(
        new ChangeRequest(socketChannel,
          ChangeRequest.REGISTER,
          SelectionKey.OP_CONNECT)
      );

      if(isLoggable(Level.INFO))
        info("added_op_connect", socketChannel.hashCode(), socketChannel);
    }

    // 1 HTTP Parser per 1 Socket Connection.

    HttpResponseParser socketParser =
      new HttpResponseParser(this, socketChannel);

    parserThreadPool.execute(socketParser);

    synchronized(httpParsers) {
      httpParsers.put(socketChannel, socketParser);
    }

    if(isLoggable(Level.INFO))
      info("new_socket", socketChannel.hashCode(), socketChannel);

    return socketChannel;
  }

  private Selector initSelector() throws IOException {
    // Create a new selector
    return SelectorProvider.provider().openSelector();
  }

  class SendRequestManager implements Runnable
  {
    private List sendRequestQueue = new LinkedList();
    private HttpClient client;

    public SendRequestManager(HttpClient client)
    {
      this.client = client;

      if(isLoggable(Level.FINE))
        log(Level.FINE, "SendRequestManager_inv");
    }

    public void add(SendRequest request)
    {
      if(isLoggable(Level.INFO))
        info("SendRequestManager1", request);

      synchronized(sendRequestQueue)
      {
        sendRequestQueue.add(request);

        if(isLoggable(Level.FINER))
          finer("SendRequestManager2", sendRequestQueue.size());

        sendRequestQueue.notify();

      }
    }

    public void run()
    {
      while(true)
      {
        SendRequest[] requests;

        synchronized(sendRequestQueue)
        {
          if(sendRequestQueue.isEmpty())
          {
            try {
              sendRequestQueue.wait();
            }
            catch(InterruptedException e) {
              throwing(this.getClass().getName(), "run", e);
            }
          }

          requests = new SendRequest[sendRequestQueue.size()];
          sendRequestQueue.toArray(requests);
          sendRequestQueue.clear();
        }

        try
        {
          if(client.isLoggable(Level.INFO))
            info("SendRequestManager4", requests.length);

          client.sendBatch(requests);
        }
        catch(IOException e) {
          throwing(this.getClass().getName(), "run", e);
        }
      }
    }
  }

  public Thread newThread(Runnable runnable)
  {
    Thread thread = new Thread(
      runnable,
      HTTP_PARSER_THREAD_NAME_PREFIX + (HTTP_PARSER_THREAD_COUNTER++)
    );
    thread.setDaemon(true);
    return thread;
  }

  private static final String HTTP_PARSER_THREAD_NAME_PREFIX =
    "sparrow-http-parser-";

  private static int HTTP_PARSER_THREAD_COUNTER = 1;

  public void setDataInductionBufferSize(int sizeInBytes)
  {
    DATA_INDUCTION_BUFFER_SIZE = sizeInBytes;
  }

  public int getDataInductionBufferSize()
  {
    return DATA_INDUCTION_BUFFER_SIZE;
  }

  public void setLogger(Logger logger) {
    log = logger;
  }

  public Logger getLogger() {
    return log;
  }




}

