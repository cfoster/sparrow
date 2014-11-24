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

import net.cfoster.sparrow.helpers.CheckSumHandler;
import net.cfoster.sparrow.helpers.TestHttpResponseHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class TransferEncodingChunkedTest
{
  private static final int SERVER_PORT = 8910;
  private Server server = null;

  final String QUICK_BROWN_FOX =
    "The quick brown fox jumps over the lazy dog";

  final byte[] QUICK_BROWN_FOX_BYTES = QUICK_BROWN_FOX.getBytes();


  @Before
  public void startJettyServer() throws Exception {
    server = new Server(SERVER_PORT);

    ServletContextHandler context =
      new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");
    server.setHandler(context);

    context.addServlet(new ServletHolder(
      new QuickBrownFoxServlet()), "/quick-brown-fox");

    context.addServlet(new ServletHolder(
      new DifferentChunkSizesServlet()), "/different-chunk-sizes");

    server.start();
  }
  @After
  public void stopJettyServer() throws Exception {
    server.stop();
  }

  @Test
  public void quickBrownFoxTest() throws Exception
  {
    HttpClient client = HttpClient.getInstance();
    HttpRequest req =
      new HttpRequest("/quick-brown-fox".getBytes(), "localhost".getBytes());

    TestHttpResponseHandler handler = new TestHttpResponseHandler();

    client.send(
      req,
      new InetSocketAddress("localhost", SERVER_PORT),
      handler
    );

    handler.waitForCompleteOrFailOrTimeOut();
    byte[] data = handler.getData();

    assertEquals(QUICK_BROWN_FOX, new String(data));
  }

  @Test
  public void testDifferentChunkSizes() throws Exception
  {
    HttpClient client = HttpClient.getInstance();
    HttpRequest req =
      new HttpRequest("/different-chunk-sizes".getBytes(), "localhost".getBytes());

    CheckSumHandler handler = new CheckSumHandler();

    client.send(
      req,
      new InetSocketAddress("localhost", SERVER_PORT),
      handler
    );

    final String EXPECTED_MD5_SUM="9F7625D5C73EEC909CBC7C9AEEB537A9";
    handler.waitForCompleteOrFailOrTimeOut();
    assertEquals(EXPECTED_MD5_SUM, handler.getChecksum());
  }



  class QuickBrownFoxServlet extends HttpServlet
  {
    @Override
    protected void doGet(
      HttpServletRequest request,
      HttpServletResponse response) throws ServletException, IOException {

      response.setHeader("Transfer-Encoding","chunked");
      response.setContentType("text/plain;charset=utf-8");
      response.setStatus(HttpServletResponse.SC_OK);
      OutputStream out = response.getOutputStream();

      out.write(QUICK_BROWN_FOX_BYTES); out.flush();
    }
  }

  class DifferentChunkSizesServlet extends HttpServlet
  {
    @Override
    protected void doGet(
      HttpServletRequest request,
      HttpServletResponse response) throws ServletException, IOException {

      response.setHeader("Transfer-Encoding","chunked");
      response.setContentType("application/octet-stream");

      Random random = new Random(1024);

      byte[] b1 = new byte[30];
      byte[] b2 = new byte[65];
      byte[] b3 = new byte[98];
      random.nextBytes(b1);
      random.nextBytes(b2);
      random.nextBytes(b3);

      response.setStatus(HttpServletResponse.SC_OK);
      OutputStream out = response.getOutputStream();

      for(int i=0;i<4;i++)
      {
        out.write(b1);
        out.flush();
        out.write(b2);
        out.flush();
        out.write(b3);
        out.flush();
      }
    }
  }

}
