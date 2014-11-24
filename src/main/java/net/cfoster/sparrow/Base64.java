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

public class Base64
{
  private static char[] map1 = new char[64];
  static {
    int i=0;
    for (char c='A'; c<='Z'; c++) map1[i++] = c;
    for (char c='a'; c<='z'; c++) map1[i++] = c;
    for (char c='0'; c<='9'; c++) map1[i++] = c;
    map1[i++] = '+'; map1[i++] = '/'; }

  private static byte[] map2 = new byte[128];
  static {
    for (int i=0; i<map2.length; i++) map2[i] = -1;
    for (int i=0; i<64; i++) map2[map1[i]] = (byte)i; }

  public static char[] encode (byte[] in) {
    return encode(in,in.length); }

  public static char[] encode (byte[] in, int iLen) {
    int oDataLen = (iLen*4+2)/3;
    int oLen = ((iLen+2)/3)*4;
    char[] out = new char[oLen];
    int ip = 0;
    int op = 0;
    while (ip < iLen) {
      int i0 = in[ip++] & 0xff;
      int i1 = ip < iLen ? in[ip++] & 0xff : 0;
      int i2 = ip < iLen ? in[ip++] & 0xff : 0;
      int o0 = i0 >>> 2;
      int o1 = ((i0 &   3) << 4) | (i1 >>> 4);
      int o2 = ((i1 & 0xf) << 2) | (i2 >>> 6);
      int o3 = i2 & 0x3F;
      out[op++] = map1[o0];
      out[op++] = map1[o1];
      out[op] = op < oDataLen ? map1[o2] : '='; op++;
      out[op] = op < oDataLen ? map1[o3] : '='; op++; }
    return out; }

  private Base64() {}

}