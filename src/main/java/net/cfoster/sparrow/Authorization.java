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

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

// [TODO]: This whole class needs to be improved.

// Digest realm="public", qop="auth", nonce="5ac1553e960e72678340aaf2f4862819", opaque="398923fc7b5fef54"
// Digest nonce="00000000000000000000000000000000", qop="xxx", nc="00000000", cnonce="00000000000000000000000000000000", response="00000000000000000000000000000000", opaque="0000000000000000", username="xxx", realm="xxx", uri="xxx"
class Authorization
{
  private static Random random = new Random(1);

  private static final Object nonceCounterLock = new Object();
  private static int nonceCounter = 0;

  private String realm = null;
  private String nonce = null;
  private String qop = null;
  private String opaque = null;
  private String cNonce = null;

  private byte[] b_realm  = null;
  private byte[] b_nonce  = null;
  private byte[] b_qop    = null;
  private byte[] b_opaque = null;
  private byte[] b_cNonce = null;

  /* Digest */
  private static final byte[] DIGEST = new byte[] { 68, 105, 103, 101, 115, 116 };

  /** username **/
  private static final byte[] USERNAME = new byte[] { 117, 115, 101, 114, 110, 97, 109, 101 };

  /** realm **/
  private static final byte[] REALM = new byte[] { 114, 101, 97, 108, 109 };

  /** nonce **/
  private static final byte[] NONCE = new byte[] { 110, 111, 110, 99, 101 };

  /** uri **/
  private static final byte[] URI = new byte[] { 117, 114, 105 };

  /** qop **/
  private static final byte[] QOP = new byte[] { 113, 111, 112 };

  /** nc **/
  private static final byte[] NC = new byte[] { 110, 99 };

  /** cnonce **/
  private static final byte[] CNONCE = new byte[] { 99, 110, 111, 110, 99, 101 };

  /** response **/
  private static final byte[] RESPONSE = new byte [] { 114, 101, 115, 112, 111, 110, 115, 101 };

  /** opaque **/
  private static final byte[] OPAQUE = new byte[] { 111, 112, 97, 113, 117, 101 };

  /** =" **/
  private static final byte[] EQUAL_QUOTE = new byte[] { 61, 34 };

  /** ', ' **/
  private static final byte[] COMMA_SPACE = new byte[] { 44, 32 };

  private static ThreadLocal md5Container = new ThreadLocal();

  private static final int AUTH_BASIC  = 1;
  private static final int AUTH_DIGEST = 2;
  private static int authenticationMethod = 0; // 0=unknown / unsupported

  /** a temporary local reusable buffer **/
  private byte[] persistentResponseBuffer = new byte[512];
  /** position of response value within persistentResponseBuffer **/
  private int responseIndex = 0;
  /** position of nc value within persistentResponseBuffer **/
  private int ncIndex = 0;
  /** length of persistentResponseBuffer **/
  private int pBufferLength=0;

  private static final byte COLON = (byte)':';



  public Authorization(byte[] authorizationHeader)
  {
    try {
      md5Container.set(MessageDigest.getInstance("MD5"));
    } catch (NoSuchAlgorithmException e) {
      // this really shouldn't happen
      throw new RuntimeException(e);
    }

    parseDigestChallengeHeader(authorizationHeader);
  }

  private String toHttpBasicAuth(String user, String password)
  {
    try {
      return ("Basic " + new String(Base64.encode((user + ":" + password).getBytes("UTF-8"))));
      // Base64.DONT_BREAK_LINES));
    } catch (UnsupportedEncodingException e) {
      return ("Basic " + new String(Base64.encode((user + ":" + password).getBytes()))); //, Base64.DONT_BREAK_LINES));
    }
  }

  private void parseDigestChallengeHeader(byte[] header)
  {
    if(header[0] == 68 || header[0] == 100) // (D or d) Assume Digest Authentication
      authenticationMethod = AUTH_DIGEST;
    else if(header[0] == 66 || header[0] == 98) { // (B or b) Assume Basic Authentication
      authenticationMethod = AUTH_BASIC;
      return;
    }

    String challengeHeader = new String(header);

    String pairs[] = challengeHeader.substring("Digest ".length()).split(", +");

    Map<String, String> params = new HashMap<String, String>();

    for (String pair : pairs) {
      String nv[] = pair.split("=", 2);
      params.put(nv[0].toLowerCase(), nv[1].substring(1, nv[1].length() - 1));
    }

    realm = params.get("realm");
    nonce = params.get("nonce");
    qop = params.get("qop");
    opaque = params.get("opaque");

    // System.out.println("realm = "+realm);
    // System.out.println("nonce = "+nonce);
    // System.out.println("qop = "+qop);
    // System.out.println("opaque = "+opaque);

    b_realm = realm.getBytes();
    b_nonce = nonce.getBytes();
    b_qop = qop.getBytes();

    if(opaque != null) // opaque is optional - if received, should be sent back
      b_opaque = opaque.getBytes();

    byte[] bytes = new byte[16];

    synchronized(random) {
      random.nextBytes(bytes);
    }

    cNonce = Authorization.bytesToHex(bytes);
    b_cNonce = cNonce.getBytes();

    // Digest
    System.arraycopy(DIGEST, 0, persistentResponseBuffer, pBufferLength, DIGEST.length);
    pBufferLength += DIGEST.length;

    // ' '
    persistentResponseBuffer[pBufferLength++] = (byte)' ';
    pBufferLength = appendValue(NONCE, b_nonce, persistentResponseBuffer, pBufferLength, true);
    pBufferLength = appendValue(QOP, b_qop, persistentResponseBuffer, pBufferLength, true);

    if(opaque != null)
      pBufferLength = appendValue(OPAQUE, b_opaque, persistentResponseBuffer, pBufferLength, true);

    pBufferLength = appendValue(CNONCE, b_cNonce, persistentResponseBuffer, pBufferLength, true);
    pBufferLength = appendValue(REALM, b_realm, persistentResponseBuffer, pBufferLength, true);

    byte[] initialResponse = new byte[32];
    byte[] initialNc = new byte[8];
    Arrays.fill(initialResponse, (byte)'0');
    Arrays.fill(initialNc, (byte)'0');

    pBufferLength = appendValue(RESPONSE, initialResponse, persistentResponseBuffer, pBufferLength, true);
    pBufferLength = appendValue(NC, initialNc, persistentResponseBuffer, pBufferLength, true);

    // Static:
    // Digest nonce="00000000000000000000000000000000",
    // qop="xxx",
    // opaque="0000000000000000",
    // cnonce="00000000000000000000000000000000",
    // realm="xxxxx",
    //
    // Changing, but fixed length:
    // response="00000000000000000000000000000000",
    // nc="00000000",
    //
    // Changing and can be any length:
    // username="xxxx",
    // uri="xxxxx"
  }

  private final int appendValue(byte[] name, byte[] value, byte[] targetArray, int position, boolean hasMore)
  {
    // name
    System.arraycopy(name, 0, targetArray, position, name.length);
    position += name.length;

    // ="
    System.arraycopy(EQUAL_QUOTE, 0, targetArray, position, EQUAL_QUOTE.length);
    position += EQUAL_QUOTE.length;

    if(name == RESPONSE)
      responseIndex = position;
    else if(name == NC)
      ncIndex = position;

    // value
    System.arraycopy(value, 0, targetArray, position, value.length);
    position += value.length;

    targetArray[position++] = (byte)'"';

    if(hasMore)
    {
      System.arraycopy(COMMA_SPACE, 0, targetArray, position, COMMA_SPACE.length);
      position += COMMA_SPACE.length;
    }

    return position;
  }

  public byte[] getAuthorizationResponse(byte[] method, byte[] uri, byte[] user, byte[] password)
  {
    switch(authenticationMethod)
    {
      case AUTH_DIGEST:
        // return toHttpDigestAuth(new String(method), new String(uri), new String(user), new String(password)).getBytes();
        return toHttpDigestAuth(method, uri, user, password);
      case AUTH_BASIC:
        return toHttpBasicAuth(new String(user), new String(password)).getBytes();
      default:
        throw new UnsupportedOperationException("Only digest and basic access authentication are supported.");
    }
  }

  private byte[] toHttpDigestAuth(byte[] method, byte[] uri, byte[] user, byte[] password) {
    // return toHttpDigestAuth(new String(method), new String(uri), new String(user), new String(password)).getBytes();

    byte[] HA1 = digestCalcHA1(user, b_realm, password);

    int localNonceCount;

    synchronized(nonceCounterLock) {
      localNonceCount = ++nonceCounter;
    }

    // byte[] nonceCount = Integer.toHexString(localNonceCount).getBytes();
    byte[] response = digestCalcResponse(HA1, b_nonce, localNonceCount, b_cNonce, b_qop, method, uri);

    // username="", uri=""
    byte[] finalArray = new byte[pBufferLength + 19 + user.length + uri.length];
    System.arraycopy(persistentResponseBuffer, 0, finalArray, 0, pBufferLength);

    writeHexByteArray(response, 0, finalArray, responseIndex, response.length, 32);
    // writeHexByteArray(nonceCount, 0, finalArray, ncIndex, nonceCount.length, 8);

    writeHexByteArray(localNonceCount, finalArray, ncIndex, 8);

    int newLength = pBufferLength;

    newLength = appendValue(USERNAME, user, finalArray, newLength, true);
    newLength = appendValue(URI, uri, finalArray, newLength, false);

    return finalArray;

/*
    byte[] responseHeader = new byte[pBufferLength + 100];

    System.arraycopy(response, 0, persistentResponseBuffer, responseIndex, 32)


    StringBuilder buf = new StringBuilder();

    buf.append("Digest username=\"");
    buf.append(user);
    buf.append("\", realm=\"");
    buf.append(realm);
    buf.append("\", nonce=\"");
    buf.append(nonce);
    buf.append("\", uri=\"");
    buf.append(uri);
    buf.append("\", qop=\"auth\", nc=\"");
    buf.append(nonceCount);
    buf.append("\", cnonce=\"");
    buf.append(cNonce);
    buf.append("\", response=\"");
    buf.append(response);
    buf.append("\", opaque=\"");
    buf.append(opaque);
    buf.append("\"");

    return buf.toString();
*/
  }


  // www-authenticate
  private String toHttpDigestAuth(String method, String uri, String user, String password) {

    String HA1 = digestCalcHA1(user, realm, password);

    int localNonceCount;

    synchronized(nonceCounterLock) {
      localNonceCount = ++nonceCounter;
    }

    String nonceCount = Integer.toHexString(localNonceCount);
    String response = digestCalcResponse(HA1, nonce, nonceCount, cNonce, qop, method, uri);

    StringBuilder buf = new StringBuilder();

    buf.append("Digest username=\"");
    buf.append(user);
    buf.append("\", realm=\"");
    buf.append(realm);
    buf.append("\", nonce=\"");
    buf.append(nonce);
    buf.append("\", uri=\"");
    buf.append(uri);
    buf.append("\", qop=\"auth\", nc=\"");
    buf.append(nonceCount);
    buf.append("\", cnonce=\"");
    buf.append(cNonce);
    buf.append("\", response=\"");
    buf.append(response);
    buf.append("\", opaque=\"");
    buf.append(opaque);
    buf.append("\"");

    return buf.toString();
  }

  private static byte[] digestCalcResponse(byte[] HA1, byte[] nonce, int nonceCount, byte[] cNonce, byte[] qop, byte[] method, byte[] uri)
  {
    byte[] buffer = new byte[1024];
    int position = 0;

    MessageDigest digest = getMessageDigest();

    // method
    System.arraycopy(method, 0, buffer, position, method.length);
    position += method.length;

    // :
    buffer[position++] = 58;

    // uri
    System.arraycopy(uri, 0, buffer, position, uri.length);
    position += uri.length;

    digest.update(buffer, 0, position);
    byte[] HA2 = Authorization.bytesToHexByteArray(digest.digest());
    // poolMessageDigest(digest);

    position = 0;

    // HA1
    System.arraycopy(HA1, 0, buffer, position, HA1.length);
    position += HA1.length;

    // :
    buffer[position++] = 58;

    // nonce
    System.arraycopy(nonce, 0, buffer, position, nonce.length);
    position += nonce.length;

    // :
    buffer[position++] = 58;

    if(qop != null)
    {
      // nonceCount

      // position = writeHexByteArray(nonceCount, 0, buffer, position, nonceCount.length, 8);
      position = writeHexByteArray(nonceCount, buffer, position, 8);

      // System.arraycopy(nonceCount, 0, buffer, position, nonceCount.length);
      // position += nonceCount.length;

      // :
      buffer[position++] = 58;

      // nonceCount
      System.arraycopy(cNonce, 0, buffer, position, cNonce.length);
      position += cNonce.length;

      // :
      buffer[position++] = 58;

      // qop
      System.arraycopy(qop, 0, buffer, position, qop.length);
      position += qop.length;

      // :
      buffer[position++] = 58;
    }

    // HA2
    System.arraycopy(HA2, 0, buffer, position, HA2.length);
    position += HA2.length;

    // System.out.println(new String(buffer,0,position));

    // digest = getMessageDigest();
    digest.update(buffer, 0, position);
    poolMessageDigest(digest);

    return digest.digest(); // Authorization.bytesToHexByteArray(digest.digest());
  }


  private static String digestCalcResponse(String HA1, String nonce, String nonceCount, String cNonce, String qop,
                                          String method, String uri) {

    try
    {
      MessageDigest digest = MessageDigest.getInstance("MD5");

      StringBuilder plaintext = new StringBuilder();

      plaintext.append(method);
      plaintext.append(":");
      plaintext.append(uri);

      digest.update(plaintext.toString().getBytes(), 0, plaintext.length());

      String HA2 = Authorization.bytesToHex(digest.digest());

      plaintext.setLength(0);
      plaintext.append(HA1);
      plaintext.append(":");
      plaintext.append(nonce);
      plaintext.append(":");
      if (qop != null) {
        plaintext.append(nonceCount);
        plaintext.append(":");
        plaintext.append(cNonce);
        plaintext.append(":");
        plaintext.append(qop);
        plaintext.append(":");
      }
      plaintext.append(HA2);

      digest.update(plaintext.toString().getBytes(), 0, plaintext.length());

      return Authorization.bytesToHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      // this really shouldn't happen
      throw new RuntimeException(e);
    }
  }

  private static final MessageDigest getMessageDigest()
  {
    MessageDigest digest = (MessageDigest)md5Container.get();
    if(digest == null) {
      try { return MessageDigest.getInstance("MD5"); }
      catch(NoSuchAlgorithmException e) { /** will not happen **/ throw new RuntimeException(e); }
    }
    else {
      digest.reset();
      return digest;
    }
  }

  private static final void poolMessageDigest(MessageDigest digest) {
    md5Container.set(digest);
  }

  private static byte[] digestCalcHA1(byte[] userName, byte[] realm, byte[] password)
  {
    byte[] buffer = new byte[userName.length + realm.length + password.length + 2];
    buffer[userName.length] = buffer[userName.length + realm.length + 1] = COLON;
    System.arraycopy(userName, 0, buffer, 0, userName.length);
    System.arraycopy(realm, 0, buffer, userName.length + 1, realm.length);
    System.arraycopy(password, 0, buffer, userName.length + realm.length + 2, password.length);

    MessageDigest digest = getMessageDigest();
    digest.reset();
    digest.update(buffer);
    byte[] digestHex = Authorization.bytesToHexByteArray(digest.digest());
    poolMessageDigest(digest);
    return digestHex;
  }

  private static String digestCalcHA1(String userName, String realm, String password) {

    try {
      MessageDigest digest = MessageDigest.getInstance("MD5");

      StringBuilder plaintext = new StringBuilder();

      plaintext.append(userName);
      plaintext.append(":");
      plaintext.append(realm);
      plaintext.append(":");
      plaintext.append(password);

      digest.update(plaintext.toString().getBytes(), 0, plaintext.length());

      return Authorization.bytesToHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      // this really shouldn't happen
      throw new RuntimeException(e);
    }
  }

  /**
   * Simple method to convert a byte array to a hex string.
   * [TODO]: Speed MUST be improved. This is a temporary measure.
   */
  public static String bytesToHex(byte bytes[]) {
      StringBuilder buf = new StringBuilder();
      for (int i = 0; i < bytes.length; i++) {
          String hex = Integer.toHexString((bytes[i]) & 0xff);
          if (hex.length() == 1) {
              buf.append('0');
          }
          buf.append(hex);
      }
      return buf.toString();
  }

  // HEX LUT, '0','1','2', ... ,'d','e','f'
  private static final byte[] HEX_LUT = { 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 97, 98, 99, 100, 101, 102 };

  public static byte[] bytesToHexByteArray(byte bytes[])
  {
    byte[] buffer = new byte[bytes.length << 1];
    Arrays.fill(buffer, (byte)'0');

    for(int n=bytes.length-1,i=buffer.length-2;n>=0;n--,i-=2) {
      buffer[i] = HEX_LUT[(bytes[n] >> 4) & 0xF];
      buffer[i+1] = HEX_LUT[bytes[n] & 0xF];
    }

    return buffer;
  }

  /**
   * Write a set of bytes as a hex string to into an existing byte buffer
   *
   * @param src the source bytes which will be converted into HEX values.
   * @param srcPos where to start reading data from within src
   * @param dst the destination string array which will receive HEX values.
   * @param dstPos the start index of where to start writing in the destination array.
   * @param length the amount of data to read from the src data
   * @param minimumSize the minimum size of the hex string, which means trailing 0s will can be prepended.
   * @return the updated position on the target destination byte array
  **/
  public static int writeHexByteArray(
    byte[] src, int srcPos,
    byte[] dst, int dstPos,
    int length, int minimumSize)
  {
    Arrays.fill(dst, dstPos, dstPos + minimumSize, (byte)'0');
    for(int n=srcPos + length - 1, i = dstPos + Math.max(minimumSize, src.length<<1) -2; n>=srcPos && i >= 0; n--, i-=2)
    {
      dst[i] = HEX_LUT[(src[n] >> 4) & 0xF];
      dst[i+1] = HEX_LUT[src[n] & 0xF];
    }

    return dstPos + minimumSize;
  }

  /**
   * @return the updated position on the destination byte array.
  **/
  public static int writeHexByteArray(
    int data, byte[] dst, int dstPos, int minimumSize)
  {
    Arrays.fill(dst, dstPos, dstPos + minimumSize, (byte)'0');

    dst[dstPos] = HEX_LUT[(int)(data >> 28) & 0xf];
    dst[dstPos+1] = HEX_LUT[(int)(data >> 24) & 0xf];
    dst[dstPos+2] = HEX_LUT[(int)(data >> 20) & 0xf];
    dst[dstPos+3] = HEX_LUT[(int)(data >> 16) & 0xf];
    dst[dstPos+4] = HEX_LUT[(int)(data >> 12) & 0xf];
    dst[dstPos+5] = HEX_LUT[(int)(data >> 8) & 0xf];
    dst[dstPos+6] = HEX_LUT[(int)(data >> 4) & 0xf];
    dst[dstPos+7] = HEX_LUT[(int)(data) & 0xf];

    return dstPos + 8;
  }



  public static void main(String[] args)
  {
    Authorization authorization = new Authorization(
      "Digest realm=\"public\", qop=\"auth\", nonce=\"ff58cdd4bf48de9681e1f621d4d1ebf1\", opaque=\"4417d35ad3ea6c10\"".getBytes()
    );

/*
    Authorization authorization = new Authorization(
      CharBuffer.wrap("Basic realm=\"Secure Area\"")
    );
*/
    long t1 = System.currentTimeMillis();
    for(int i=0;i<10;i++) {
      byte[] buffer = authorization.getAuthorizationResponse(
        new byte[] { 'G','E','T' },
        new byte[] { '/','e','v','a','l' },
        new byte[] { 'a','d','m','i','n' },
        new byte[] { 'a','d','m','i','n' }
      );

      // System.out.println(buffer.length+" = "+new String(buffer));

      // System.out.println(new String(buffer));
    }
    long t2 = System.currentTimeMillis();
    // System.out.println("took "+(t2-t1)+" msecs.");
  }


}
