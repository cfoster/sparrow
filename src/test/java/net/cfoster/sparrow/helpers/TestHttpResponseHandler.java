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

package net.cfoster.sparrow.helpers;


import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;

/**
 * TestHttpResponseHandler records response events, waiting until
 * all events are completed, or if there was a failure, or if we
 * think the test is going no-where (timed-out).
 **/
public class TestHttpResponseHandler extends WaitingResponseHandler
{
  int statusVersion = -1;
  int statusCode = 1 ;
  String statusReasonPhrase = null;
  boolean headersCompleted = false;
  LinkedHashMap headers = new LinkedHashMap();
  ByteArrayOutputStream bufferStream = new ByteArrayOutputStream();

  public void statusLine(int version, int statusCode, byte[] reason,
                         int reasonOffset, int reasonLength)
  {
    this.statusVersion = version;
    this.statusCode = statusCode;
    this.statusReasonPhrase = new String(reason, reasonOffset, reasonLength);
  }

  public void header(byte[] data, int headerOffset,
                     int headerLength, int valueOffset, int valueLength) {
    String name = new String(data, headerOffset, headerLength);
    String value = new String(data, valueOffset, valueLength);
    headers.put(name, value);
  }

  public void headersCompleted() {
    headersCompleted = true;
  }

  public void bodyPart(byte[] data, int offset, int length)
  {
    bufferStream.write(data, offset, length);
  }

  /**
   * Whether or not this HttpResponseHandler can currently accept data
   * Generally this will always be true, in scenarios where the data that is
   * being downloaded is so large and is being physically downloaded so fast
   * that the parser can not keep up and is running out of local buffer storage
   * then this method will return false (until the parser catches up!)
   */
  public boolean acceptingData() {
    return true;
  }

  public int getStatusVersion() {
    return statusVersion;
  }

  public int getStatusCode() {
    return statusCode;
  }
/**
  public CharBuffer getStatusReasonPhrase() {
    return statusReasonPhrase;
  } **/

  public boolean isHeadersCompleted() {
    return headersCompleted;
  }

  public boolean isResponseCompleted() {
    return responseCompleted;
  }

  public boolean isConnectionFailed() {
    return connectionFailed;
  }

  public Throwable getConnectionFailedException() {
    return connectionFailedException;
  }

  public byte[] getData()
  {
    return bufferStream.toByteArray();
  }
}