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

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class CheckSumHandler extends ByteCounterHandler
{
  protected MessageDigest md5;

  public CheckSumHandler()
  {
    super();
    initMD();
  }

  public CheckSumHandler(int timeOutSeconds) {
    super(timeOutSeconds);
    initMD();
  }

  private void initMD() {
    try {
      md5 = MessageDigest.getInstance("MD5");
    } catch(NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  public void bodyPart(byte[] data, int offset, int length) {
    md5.update(data, offset, length);
    super.bodyPart(data, offset, length);
  }

  public String getChecksum() {
    return String.format("%032X", new BigInteger(1, md5.digest()));
  }

}
