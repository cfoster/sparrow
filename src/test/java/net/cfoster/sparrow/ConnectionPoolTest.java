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

import net.cfoster.sparrow.helpers.TestHttpResponseHandler;
import net.cfoster.sparrow.impl.HttpClientInspector;
import net.cfoster.sparrow.servlets.HelloWorld;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.*;

import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

import static org.junit.Assert.*;

/**
 * Tests that "existing socket connections" are being used instead of creating
 * brand new sockets all the time
 *
 * Also, it is preferable that existing HttpResponseParsers are also used.
**/
public class ConnectionPoolTest
{
  private static final int SERVER_PORT = 8910;
  private Server server = null;

  @Before
  public void startJettyServer() throws Exception {
    server = new Server(SERVER_PORT);
    ServletContextHandler context = new ServletContextHandler();

    context.addServlet(
      new ServletHolder(new HelloWorld("Hello World")), "/hello-world");

    context.addServlet(
      new ServletHolder(new HelloWorld("Goodbye World")), "/goodbye-world");

    server.setHandler(context);
    server.start();
  }

  @After
  public void stopJettyServer() throws Exception
  {
    server.stop();
  }

  private static HttpRequest request(String uri, String host, String connection)
  {
    HttpRequest request = new HttpRequest(uri.getBytes(), host.getBytes());
    request.setHeader("Connection".getBytes(), connection.getBytes());
    return request;
  }

  @Test
  public void KeepAlive() throws Exception
  {
    HttpClient client = HttpClient.getInstance();

    HttpRequest req1 = request("/hello-world", "localhost", "Keep-Alive");
    HttpRequest req2 = request("/goodbye-world", "localhost", "Keep-Alive");

    TestHttpResponseHandler handler = null;

    HttpClientInspector inspector = new HttpClientInspector(client);

    SocketChannel channel1, channel2;

    handler = new TestHttpResponseHandler();

    client.send(
      req1,
      new InetSocketAddress("localhost", SERVER_PORT),
      handler
    );

    byte[] data = null;

    handler.waitForCompleteOrFailOrTimeOut();
    channel1 = inspector.socketChannelFor(handler);
    data = handler.getData();

    assertEquals("Hello World", new String(data));

    handler = new TestHttpResponseHandler();

    client.send(
      req2,
      new InetSocketAddress("localhost", SERVER_PORT),
      handler
    );

    handler.waitForCompleteOrFailOrTimeOut();
    channel2 = inspector.socketChannelFor(handler);
    data = handler.getData();

    assertSame("Should be re-using the same connection.", channel1, channel2);
    assertEquals("Goodbye World", new String(data));
  }

  @Test
  public void Close() throws Exception
  {
    HttpClient client = HttpClient.getInstance();

    HttpRequest req1 = request("/hello-world", "localhost", "Close");
    HttpRequest req2 = request("/goodbye-world", "localhost", "Close");

    TestHttpResponseHandler handler = null;

    HttpClientInspector inspector = new HttpClientInspector(client);

    SocketChannel channel1, channel2;

    handler = new TestHttpResponseHandler();

    client.send(
      req1,
      new InetSocketAddress("localhost", SERVER_PORT),
      handler
    );

    byte[] data = null;

    handler.waitForCompleteOrFailOrTimeOut();

    channel1 = inspector.socketChannelFor(handler);
    data = handler.getData();

    assertEquals("Hello World", new String(data));

    handler = new TestHttpResponseHandler();

    client.send(
      req2,
      new InetSocketAddress("localhost", SERVER_PORT),
      handler
    );

    handler.waitForCompleteOrFailOrTimeOut();
    channel2 = inspector.socketChannelFor(handler);
    data = handler.getData();

    assertNotSame("Should be different connections.", channel1, channel2);
    assertEquals("Goodbye World", new String(data));
  }

}
