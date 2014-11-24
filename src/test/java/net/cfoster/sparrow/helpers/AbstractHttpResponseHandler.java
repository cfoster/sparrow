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

import net.cfoster.sparrow.HttpResponseHandler;
import net.cfoster.sparrow.HttpResponseParser;

public class AbstractHttpResponseHandler implements HttpResponseHandler
{
  public void statusLine(int version, int statusCode, byte[] reason,
                         int reasonOffset, int reasonLength) {
  }

  public void header(byte[] data, int headerOffset, int headerLength,
                     int valueOffset, int valueLength) {
  }

  public void headersCompleted() {
  }

  public void bodyPart(byte[] data, int offset, int length) {
  }

  public void completed() {
  }

  public void connectionFailed(Throwable exception) {
  }

  public void setAcceptingDataListener(HttpResponseParser parser) {
  }

}
