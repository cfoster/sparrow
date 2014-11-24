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

package perf;

import net.cfoster.sparrow.helpers.ByteCounterHandler;
import net.cfoster.sparrow.HttpClient;
import net.cfoster.sparrow.HttpRequest;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class Streaming400GB
{
  static int SERVER_PORT = 8910;
  static Server server;

  public static void main(String[] args) throws Exception
  {
    setupJetty();
    // Thread.sleep(60 * 1000 * 6);
    run();

    shutdownJetty();
  }

  public static void setupJetty() throws Exception
  {
    server = new Server(SERVER_PORT);

    server.setHandler(new AbstractHandler()
    {
      public void handle(String target, Request baseRequest,
                         HttpServletRequest request,
                         HttpServletResponse response)
        throws IOException, ServletException
      {
        response.setHeader("Transfer-Encoding","chunked");
        response.setContentType("application/octet-stream");
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);

        final byte[] buffer = new byte[1024*1024];

        for(int i=0;i<1024*1024;i++)
          buffer[i] = (byte) (i & 0xFF);

        OutputStream out = response.getOutputStream();

        for(int i=0;i<1 * 1024 * 400;i++) {
          out.write(buffer);
          out.flush();
        }

        out.flush();

      }
    });

    server.start();
  }

  public static void run() throws Exception
  {
    HttpClient client = HttpClient.getInstance();
    HttpRequest req = new HttpRequest("/".getBytes(), "localhost".getBytes());

    ByteCounterHandler handler = new ByteCounterHandler(60 * 5);

    long t1 = System.currentTimeMillis();

    client.send(
      req,
      new InetSocketAddress("localhost", SERVER_PORT),
      handler
    );
    System.out.println("sent request");

    handler.waitForCompleteOrFailOrTimeOut();

    long t2 = System.currentTimeMillis();

    System.out.println(
      "received " + (handler.getTotalBytesReceived()/1024/1024) + " mb in "+
        ((t2-t1) / 1000f ) + " seconds");

  }

  public static void shutdownJetty() throws Exception
  {
    server.stop();
  }
}
