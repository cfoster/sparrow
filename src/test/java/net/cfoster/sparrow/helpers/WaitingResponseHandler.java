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

public class WaitingResponseHandler extends AbstractHttpResponseHandler
{
  final int TIMEOUT_IN_SECONDS;
  protected boolean responseCompleted = false;
  protected boolean connectionFailed = false;
  protected Throwable connectionFailedException = null;
  protected Object lock = new Object();

  public WaitingResponseHandler()
  {
    this(40);
  }

  public WaitingResponseHandler(int timeOutInSeconds)
  {
    TIMEOUT_IN_SECONDS = timeOutInSeconds;
  }

  public void completed() {
    responseCompleted = true;
    synchronized(lock) {
      lock.notify();
    }
  }

  /**
   * Waits for the response to complete, fail or timeout.
   * @return whether or not the response completed successfully.
   */
  public boolean waitForCompleteOrFailOrTimeOut()
  {
    try
    {
      synchronized(lock)
      {
        lock.wait(TIMEOUT_IN_SECONDS * 1000l);
      }
    }
    catch(InterruptedException e) { }

    return responseCompleted;
  }

  public void connectionFailed(Throwable exception) {
    connectionFailed = true;
    connectionFailedException = exception;
    synchronized(lock) {
      lock.notify();
    }
  }

}
