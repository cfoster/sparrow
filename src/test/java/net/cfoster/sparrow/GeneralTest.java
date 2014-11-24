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

import net.cfoster.sparrow.HttpResponseParser;
import org.junit.Test;
import sun.misc.Unsafe;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class GeneralTest
{

  @Test
  public void testHexChar2int()
  {
    assertEquals(0, HttpResponseParser.hexChar2int((byte) '0'));
    assertEquals(1, HttpResponseParser.hexChar2int((byte)'1'));
    assertEquals(2, HttpResponseParser.hexChar2int((byte)'2'));
    assertEquals(3, HttpResponseParser.hexChar2int((byte)'3'));
    assertEquals(4, HttpResponseParser.hexChar2int((byte)'4'));
    assertEquals(5, HttpResponseParser.hexChar2int((byte)'5'));
    assertEquals(6, HttpResponseParser.hexChar2int((byte)'6'));
    assertEquals(7, HttpResponseParser.hexChar2int((byte)'7'));
    assertEquals(8, HttpResponseParser.hexChar2int((byte)'8'));
    assertEquals(9, HttpResponseParser.hexChar2int((byte)'9'));
    assertEquals(10, HttpResponseParser.hexChar2int((byte)'A'));
    assertEquals(11, HttpResponseParser.hexChar2int((byte)'B'));
    assertEquals(12, HttpResponseParser.hexChar2int((byte)'C'));
    assertEquals(13, HttpResponseParser.hexChar2int((byte)'D'));
    assertEquals(14, HttpResponseParser.hexChar2int((byte)'E'));
    assertEquals(15, HttpResponseParser.hexChar2int((byte)'F'));
    assertEquals(10, HttpResponseParser.hexChar2int((byte)'a'));
    assertEquals(11, HttpResponseParser.hexChar2int((byte)'b'));
    assertEquals(12, HttpResponseParser.hexChar2int((byte)'c'));
    assertEquals(13, HttpResponseParser.hexChar2int((byte)'d'));
    assertEquals(14, HttpResponseParser.hexChar2int((byte)'e'));
    assertEquals(15, HttpResponseParser.hexChar2int((byte)'f'));
  }

  @Test
  public void testIsNibble()
  {
    for(int i=0;i<16;i++)
      assertTrue(i+" is a nibble.", HttpResponseParser.isNibble(i));

    for(int i=-1;i>-4096;i--)
      assertFalse(i+" is not a nibble.", HttpResponseParser.isNibble(i));

    for(int i=16;i<4096;i++)
      assertFalse(i+" is not a nibble.", HttpResponseParser.isNibble(i));
  }

  /**
   * All characters ranging outside [a-zA-Z0-9] should not be valid nibbles
   * when parsed with hexChar2int
   */
  @Test
  public void ASCII()
  {
    for(int i=0;i<255;i++)
    {
      byte b = (byte)i;

      if(!(b>='0' && b<='9' || b>='A' && b<='F' || b>='a' && b<='f'))
      {
        assertFalse(
          "['"+((char)b)+"', or "+i+"] should NOT be a nibble.",
          HttpResponseParser.isNibble(HttpResponseParser.hexChar2int(b))
        );
      }

    }
  }

  @Test
  public void copyRegionToRegion()
  {
    ByteBuffer src = ByteBuffer.allocateDirect(9);
    src.put(new byte[] { 100, 101, 102, 103, 104, 105, 106, 107, 108 });

    Unsafe unsafe = HttpResponseParser.getUnsafe();
    long toAddr = unsafe.allocateMemory(9);

    HttpResponseParser.copy(HttpResponseParser.address(src), 0, toAddr, 0, 9);

    try {
      for(int i=0;i<9;i++)
        assertEquals(i + 100, unsafe.getByte(toAddr + i));
    } finally {
      unsafe.freeMemory(toAddr);
    }
  }

  private final void reset(byte[] arr) {
    Arrays.fill(arr, (byte)0);
  }

  @Test
  public void copyRegionToArray()
  {
    ByteBuffer src = ByteBuffer.allocateDirect(9);
    src.put(new byte[] { 100, 101, 102, 103, 104, 105, 106, 107, 108 });

    byte[] toAddr = new byte[9];

    // read all in one go
    HttpResponseParser.copy(HttpResponseParser.address(src), 0, toAddr, 0, 9);
    for(int i=0;i<9;i++)
      assertEquals(i + 100, toAddr[i]);
    reset(toAddr);

    // read byte by byte
    for(int i=0;i<9;i++)
      HttpResponseParser.copy(HttpResponseParser.address(src), i, toAddr, i, 1);
    for(int i=0;i<9;i++)
      assertEquals(i + 100, toAddr[i]);
    reset(toAddr);

    // don't read too much - 1
    HttpResponseParser.copy(HttpResponseParser.address(src), 0, toAddr, 0, 4);
    for(int i=0;i<4;i++) assertEquals(i + 100, toAddr[i]);
    for(int i=4;i<9;i++) assertEquals(0, toAddr[i]);
    reset(toAddr);

    // don't read too much - 2
    HttpResponseParser.copy(HttpResponseParser.address(src), 4, toAddr, 0, 4);
    for(int i=0;i<4;i++) assertEquals(4 + i + 100, toAddr[i]);
    for(int i=4;i<9;i++) assertEquals(0, toAddr[i]);
    reset(toAddr);

    // don't read too much - 3
    HttpResponseParser.copy(HttpResponseParser.address(src), 4, toAddr, 4, 4);
    for(int i=4;i<8;i++) assertEquals(i + 100, toAddr[i]);
    for(int i=0;i<4;i++) assertEquals(0, toAddr[i]);
    assertEquals(0, toAddr[8]);
    reset(toAddr);
  }

  @Test
  public void copyLargeRegionToLargeArray()
  {
    final int SIZE = 1024 * 1024;

    ByteBuffer src = ByteBuffer.allocateDirect(SIZE);
    byte[] toAddr = new byte[SIZE];

    for(int i=0;i<SIZE;i++)
      src.put((byte)i);

    HttpResponseParser.copy(
      HttpResponseParser.address(src), 0, toAddr, 0, SIZE
    );

    for(int i=0;i<SIZE;i++)
      assertEquals("toAddr["+i+"]", (byte)i, toAddr[i]);
  }


}
