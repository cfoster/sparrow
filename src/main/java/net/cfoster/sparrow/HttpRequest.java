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
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.*;

public class HttpRequest
{
  public static final int METHOD_GET = 0;
  public static final int METHOD_POST = 1;
  public static final int METHOD_PUT = 2;

  private Map headers = new HashMap();
  private Map cookies = new HashMap();

  /** prepended to PUT statements (likely to be very small) **/
  private byte[] bodyPrefix = null;

  /** for PUT requests (the main chunk, could be very large) **/
  private ByteBuffer requestBody = null;

  /** appended to POST statements (likely to be very small) **/
  private byte[] bodySuffix = null;

  private int requestMethod = METHOD_GET;

  private byte[] uri;
  private byte[] user;
  private byte[] password;

  private int authorizationFailedAttempts = 0;

  private byte[][] parameterNames = new byte[15][];
  private ByteBuffer[] parameterValues = new ByteBuffer[15];
  private int parameterCount = 0;

  private final ByteBuffer EQUALS_BYTE_BUFFER =
    ByteBuffer.wrap(new byte[] { 61 });
  private final ByteBuffer AMPERSAND_BYTE_BUFFER =
    ByteBuffer.wrap(new byte[] { 38 });

  private static final byte[] STR_HOST = { 72, 111, 115, 116 }; // Host

  public HttpRequest(byte[] uri, byte[] host)
  {
    setUri(uri);
    setHeader(STR_HOST, host);
  }

  public void setUri(byte[] uri) {
    this.uri = uri;
  }

  public byte[] getUri() {
    return uri;
  }

  public Set getHeaderNames() {
    return headers.keySet();
  }

  public void setHeader(byte[] name, byte[] value) {
    headers.put(name, value);
  }

  public byte[] getHeader(byte[] name) {
    return (byte[])headers.get(name);
  }

  public Set getCookieNames() {
    return cookies.keySet();
  }

  public void setCookie(byte[] name, byte[] value) {
    cookies.put(name, value);
  }

  public byte[] getCookie(byte[] name) {
    return (byte[])cookies.get(name);
  }



  public Set getParameterNames() {
    HashSet set = new HashSet(parameterCount);

    for(int i=0;i<parameterCount;i++)
      set.add(parameterNames[i]);

    return set;
  }

  /**
   * Sets a Request Parameter. The value must be pre-URL-encoded and be ready
   * to send.
   *
   * @param name the name of the HTTP parameter.
   * @param value the value of the HTTP parameter.
   */
  public void addParameter(byte[] name, ByteBuffer value)
  {
    if(parameterCount == parameterNames.length)
      growParameterCapacity();

    parameterNames[parameterCount] = name;
    parameterValues[parameterCount] = value;

    parameterCount++;
  }

  /**
   * Sets a Request Parameter. The value must be pre-URL-encoded and be ready
   * to send.
   *
   * @param name the name of the HTTP parameter.
   * @param value the value of the HTTP parameter.
   */
  public void addParameter(byte[] name, byte[] value)
  {
    addParameter(name, ByteBuffer.wrap(value));
  }

  /**
   * Adds a parameter flag (a name with no value)
   * @param name the name of the HTTP parameter.
   */
  public void addParameterFlag(byte[] name)
  {
    addParameter(name, (ByteBuffer)null);
  }

  public boolean hasParameter(byte[] name)
  {
    for(int i=0;i<parameterNames.length;i++)
      if(Arrays.equals(name, parameterNames[i]))
        return true;

    return false;
  }

  public void writeHttpParameters(WritableByteChannel out) throws IOException
  {
    for(int i=0;i<parameterCount;i++)
    {
      byte[] name = parameterNames[i];

      HttpClient.forceWrite(out, ByteBuffer.wrap(name));

      ByteBuffer value = parameterValues[i];

      if(value != null)
      {
        if(value != null)
        {
          EQUALS_BYTE_BUFFER.rewind();
          HttpClient.forceWrite(out, EQUALS_BYTE_BUFFER);
          HttpClient.forceWrite(out, value.asReadOnlyBuffer());
        }

        if(i + 1 != parameterCount)
        {
          AMPERSAND_BYTE_BUFFER.rewind();
          HttpClient.forceWrite(out, AMPERSAND_BYTE_BUFFER);
        }
      }
    }
  }

  public ByteBuffer[] getParameters(byte[] name)
  {
    int length = 0;

    for(int i=0;i<parameterNames.length;i++)
      if(Arrays.equals(name, parameterNames[i]) && parameterValues[i] != null)
        length++;

    ByteBuffer[] retValue = new ByteBuffer[length];

    for(int i=0,n=0;i<parameterNames.length;i++)
      if(Arrays.equals(name, parameterNames[i]) && parameterValues[i] != null)
          retValue[n++] = parameterValues[i];

    return retValue;
/*
    ArrayList list = (ArrayList)parameters.get(name);
    ByteBuffer[] byteBufferArray = new ByteBuffer[list.size()];
    list.toArray(byteBufferArray);
    return byteBufferArray;
*/
  }
/*
  public ByteBuffer getParameter(byte[] name) {
    ByteBuffer buffer = (ByteBuffer)parameters.get(name);
    buffer.rewind();
    return buffer;
  }
*/

  public byte[] getUser() {
    return user;
  }

  public void setUser(byte[] user) {
    this.user = user;
  }

  public byte[] getPassword() {
    return password;
  }

  public boolean hasParameters() {
    return parameterCount != 0;
  }

  public void setPassword(byte[] password) {
    this.password = password;
  }

  public void setMethod(int requestMethod) {
    this.requestMethod = requestMethod;
  }

  public int getMethod() {
    return requestMethod;
  }

  public ByteBuffer getQueryString() {
    return null;
  }

  /**
   * Set message body, e.g. for a PUT message.
   *
   * The buffer must be pre-formatted, pre-encoded, etc and
   * "completely ready to go" straight off.
   *
   * @param bodyBuffer
   */
  public void setBody(ByteBuffer bodyBuffer)
  {
    requestBody = bodyBuffer;
  }

  public void setBody(byte[] heapArray)
  {
    requestBody = ByteBuffer.wrap(heapArray);
  }

  public ByteBuffer getBody()
  {
    requestBody.rewind();
    return requestBody;
  }

  public int getContentLength()
  {
    int count = 0;

    switch(requestMethod)
    {
      case METHOD_POST:
        Iterator iterator = getParameterNames().iterator();

        while(iterator.hasNext())
        {
          byte[] name = (byte[])iterator.next();

          ByteBuffer[] bufferArray = getParameters(name);

          for(int i=0;i<bufferArray.length;i++)
          {
            count += bufferArray[i].limit();
            count += name.length + 1; // + 1 for =

            if(i+1 < bufferArray.length)
              count ++; // for &
          }

          if(iterator.hasNext())
            count ++; // for &
        }
      break;
      case METHOD_PUT:

        if(bodyPrefix != null)
          count += bodyPrefix.length;

        count += requestBody.limit();

        if(bodySuffix != null)
          count += bodySuffix.length;

      break;
      case METHOD_GET:
        return -1; // Not Applicable
    }

    return count;
  }


  private void growParameterCapacity()
  {
    byte[][] newParameterNames = new byte[parameterCount << 1][];
    ByteBuffer[] newParameterValues = new ByteBuffer[parameterCount << 1];

    System.arraycopy(parameterNames, 0, newParameterNames, 0, parameterCount);
    System.arraycopy(parameterValues, 0, newParameterValues, 0, parameterCount);

    parameterNames = newParameterNames;
    parameterValues = newParameterValues;
  }

  public byte[] getBodyPrefix() {
    return bodyPrefix;
  }

  public void setBodyPrefix(byte[] bodyPrefix) {
    this.bodyPrefix = bodyPrefix;
  }

  public byte[] getBodySuffix() {
    return bodySuffix;
  }

  public void setBodySuffix(byte[] bodySuffix) {
    this.bodySuffix = bodySuffix;
  }

  public void addFailedAuthorizationAttempt()
  {
    authorizationFailedAttempts++;
  }

  public boolean tooManyFailedAuthorizationAttempts()
  {
    return authorizationFailedAttempts >= 1;
  }


}
