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

import net.cfoster.sparrow.HttpResponseParser;

public class VerboseHandler extends CheckSumHandler
{
  public VerboseHandler()
  {
    super();
  }

  public VerboseHandler(int timeoutSeconds)
  {
    super(timeoutSeconds);
  }

  public void statusLine(int version, int statusCode, byte[] reason,
                         int reasonOffset, int reasonLength)
  {
    System.out.println(
      statusCode+" - "+new String(reason,reasonOffset,reasonLength)
    );

    super.statusLine(version, statusCode, reason, reasonOffset, reasonLength);
  }

  public void header(byte[] data, int headerOffset, int headerLength,
                     int valueOffset, int valueLength) {

    System.out.println(
      new String(data, headerOffset, headerLength) + ": " +
        new String(data, valueOffset, valueLength)
    );

    super.header(data, headerOffset, headerLength, valueOffset, valueLength);
  }

  public void headersCompleted() {

    System.out.println("headersCompleted()");
    super.headersCompleted();
  }

  private int counter;

  public void bodyPart(byte[] data, int offset, int length) {
    super.bodyPart(data, offset, length);

    long totalReceived = getTotalBytesReceived();

    if(counter++ % 1000 == 0) {
      System.out.println("Downloaded " + (totalReceived/1024f/1024f)+" megs.");
    }
  }

  public void completed() {
    System.out.println("completed()");
    new Exception().printStackTrace();
    super.completed();
  }

  public void connectionFailed(Throwable exception) {
    System.out.println("connectionFailed()");
    exception.printStackTrace();
    super.connectionFailed(exception);
  }

  public void setAcceptingDataListener(HttpResponseParser parser) {
    System.out.println("setAcceptingDataListener("+parser+")");
    super.setAcceptingDataListener(parser);
  }

}
