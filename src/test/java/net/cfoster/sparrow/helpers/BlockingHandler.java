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

public class BlockingHandler extends CheckSumHandler
{
  /** Whether or not this handler has waited for a period of time **/
  protected boolean waited = false;

  protected long waitMillis;

  public BlockingHandler(int seconds) {
    super();
    this.waitMillis = seconds * 1000l;
  }

  @Override
  public void bodyPart(byte[] data, int offset, int length) {

    super.bodyPart(data, offset, length);

    if(getTotalBytesReceived() > 1024 * 1024 && !waited) {
      try {

        System.out.println("handler sleeping for " +
          (waitMillis / 1000) + " seconds.");

        Thread.sleep(waitMillis);
        waited = true;
      } catch(InterruptedException e) {
        e.printStackTrace();
      }
    }

  }
}
