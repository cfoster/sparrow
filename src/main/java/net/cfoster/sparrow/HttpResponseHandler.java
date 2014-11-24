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

public interface HttpResponseHandler
{
  /**
   * Invoked when a HTTP Response Status Line is received.
   * @param version the HTTP response version of the server.
   * @param statusCode the HTTP response status code.
   */
  public abstract void statusLine(
    int version,
    int statusCode,
    byte[] reason,
    int reasonOffset, int reasonLength);

  /**
   * Each HTTP header is piped through this function
  **/
  // public abstract void header(CharBuffer name, CharBuffer value);
  public abstract void header(
    byte[] data,
    int headerOffset, int headerLength,
    int valueOffset, int valueLength);

  /**
   * Invoked once all HTTP Response headers have been received.
   * The next events should be HTTP body parts.
   */
  public abstract void headersCompleted();

  /**
   * HTTP message body content is piped through this function
   * @param data a chunk of data from the HTTP response body.
  **/
  public abstract void bodyPart(byte[] data, int offset, int length);

  /**
   * Called when the HTTP response has completed.
  **/
  public abstract void completed();

  /**
   * Invoked if the connection to the server failed.
   * @param exception additional information
   */
  public abstract void connectionFailed(Throwable exception);

  /**
   * Whether or not this HttpResponseHandler can currently accept data
   * Generally this will always be true, in scenarios where the data that is
   * being downloaded is so large and is being physically downloaded so fast
   * that the parser can not keep up and is running out of local buffer storage
   * then this method will return false (until the parser catches up!)
  **/
  public abstract void setAcceptingDataListener(
    HttpResponseParser parser
  );
}
