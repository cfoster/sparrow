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

import net.cfoster.sparrow.servlets.RedirectServlet;
import net.cfoster.sparrow.servlets.HelloWorld;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.After;
import org.junit.Before;

import static org.junit.Assert.assertEquals;

/**
 * From Wikipedia:
 *
 * 300 Multiple Choices
 * Indicates multiple options for the resource that the client may follow. It,
 * for instance, could be used to present different format options for video,
 * list files with different extensions, or word sense disambiguation.
 *
 * 301 Moved Permanently
 * This and all future requests should be directed to the given URI.
 *
 * 302 Found
 * This is an example of industry practice contradicting the standard.
 * The HTTP/1.0 specification (RFC 1945) required the client to perform a
 * temporary redirect (the original describing phrase was
 * "Moved Temporarily"),[5] but popular browsers implemented 302 with the
 * functionality of a 303 See Other. Therefore, HTTP/1.1 added status codes 303
 * and 307 to distinguish between the two behaviours.[6] However, some Web
 * applications and frameworks use the 302 status code as if it were the 303.[7]
 *
 * 303 See Other (since HTTP/1.1)
 * The response to the request can be found under another URI using a GET
 * method. When received in response to a POST (or PUT/DELETE), it should be
 * assumed that the server has received the data and the redirect should be
 * issued with a separate GET message.
 *
 * 304 Not Modified
 * Indicates that the resource has not been modified since the version specified
 * by the request headers If-Modified-Since or If-Match. This means that there
 * is no need to retransmit the resource, since the client still has a
 * previously-downloaded copy.
 *
 * 305 Use Proxy (since HTTP/1.1)
 * The requested resource is only available through a proxy, whose address is
 * provided in the response. Many HTTP clients (such as Mozilla[8] and Internet
 * Explorer) do not correctly handle responses with this status code, primarily
 * for security reasons.[9]
 *
 * 306 Switch Proxy
 * No longer used. Originally meant "Subsequent requests should use the
 * specified proxy."[10]
 *
 * 307 Temporary Redirect (since HTTP/1.1)
 * In this case, the request should be repeated with another URI; however,
 * future requests should still use the original URI. In contrast to how 302 was
 * historically implemented, the request method is not allowed to be changed
 * when reissuing the original request. For instance, a POST request should be
 * repeated using another POST request.[11]
 *
 * 308 Permanent Redirect (Experimental RFC; RFC 7238)
 * The request, and all future requests should be repeated using another URI.
 * 307 and 308 (as proposed) parallel the behaviours of 302 and 301, but do not
 * allow the HTTP method to change. So, for example, submitting a form to a
 * permanently redirected resource may continue smoothly.[12]
 */

public class RedirectsTest
{
  private static final int SERVER_PORT = 8910;
  private Server server = null;

  @Before
  public void startJettyServer() throws Exception {
    server = new Server(SERVER_PORT);
    ServletContextHandler context = new ServletContextHandler();

    context.addServlet(
      new ServletHolder(
        new HelloWorld()
      ), "/hello-world"
    );

    context.addServlet(
      new ServletHolder(
        new RedirectServlet(301)
      ), "/hello-world/301"
    );

    server.setHandler(context);
    server.start();
  }

  @After
  public void stopJettyServer() throws Exception {
    server.stop();
  }

  /**
  @Test
  public void test301() throws Exception
  {
    HttpClient client = HttpClient.getInstance();
    HttpRequest req = new HttpRequest(
      "/hello-world/301".getBytes(), "localhost".getBytes());

    TestHttpResponseHandler handler = new TestHttpResponseHandler();

    client.send(
      req,
      new InetSocketAddress("localhost", SERVER_PORT),
      handler
    );

    handler.waitForCompleteOrFailOrTimeOut();
    byte[] data = handler.getData();

    assertEquals("Hello World", new String(data));
  }
  **/

}

