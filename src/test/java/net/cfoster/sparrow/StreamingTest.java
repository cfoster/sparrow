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
import net.cfoster.sparrow.servlets.StreamingDataServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.text.NumberFormat;

import static org.junit.Assert.assertEquals;

public class StreamingTest
{
  private static final int SERVER_PORT = 8910;
  private Server server = null;

  static final long SIZE_1GB = 1024L * 1024L * 1024L;
  static final long SIZE_10GB = 1024L * 1024L * 1024L * 10;

  @Before
  public void startJettyServer() throws Exception {
    server = new Server(SERVER_PORT);
    ServletContextHandler context = new ServletContextHandler();

    StreamingDataServlet oneGB = new StreamingDataServlet(
      SIZE_1GB
    );

    StreamingDataServlet tenGB = new StreamingDataServlet(
      SIZE_10GB
    );

    context.addServlet(new ServletHolder(oneGB), "/1");
    context.addServlet(new ServletHolder(tenGB), "/10");

    server.setHandler(context);
    server.start();
  }

  @After
  public void stopJettyServer() throws Exception {
    server.stop();
  }

  @Test
  public void stream1GB() throws Exception
  {
    HttpClient client = HttpClient.getInstance();
    HttpRequest req = new HttpRequest("/1".getBytes(), "localhost".getBytes());

    CheckSumHandler handler = new CheckSumHandler();

    client.send(
      req,
      new InetSocketAddress("localhost", SERVER_PORT),
      handler
    );

    handler.waitForCompleteOrFailOrTimeOut();

    float diff = ((SIZE_1GB)-handler.getTotalBytesReceived())/1024f;

    assertEquals(
      "Should have downloaded a certain amount of bytes. [diff="+
        NumberFormat.getInstance().format(diff)
        +" KB].",
      SIZE_1GB,
      handler.getTotalBytesReceived()
    );

    assertEquals(
      "invalid checksum of response",
      "CB17F4AB872D64DB60B980A67CF04A8A",
      handler.getChecksum()
    );
  }

  @Test
  public void stream10GB() throws Exception
  {
    HttpClient client = HttpClient.getInstance();
    HttpRequest req = new HttpRequest("/10".getBytes(), "localhost".getBytes());

    CheckSumHandler handler = new CheckSumHandler();

    client.send(
      req,
      new InetSocketAddress("localhost", SERVER_PORT),
      handler
    );

    handler.waitForCompleteOrFailOrTimeOut();

    float diff=((SIZE_10GB)-handler.getTotalBytesReceived())/1024f;

    assertEquals(
      "Should have downloaded a certain amount of bytes. [diff="+
        NumberFormat.getInstance().format(diff)
        +" KB].",
      SIZE_10GB,
      handler.getTotalBytesReceived()
    );

    assertEquals(
      "invalid checksum of response",
      "71D5A3E7508282C964550D202382A159",
      handler.getChecksum()
    );
  }

}
