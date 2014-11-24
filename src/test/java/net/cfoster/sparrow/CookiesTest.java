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

import net.cfoster.sparrow.servlets.HelloWorld;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.After;
import org.junit.Before;

public class CookiesTest
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

    server.setHandler(context);
    server.start();
  }

  @After
  public void stopJettyServer() throws Exception {
    server.stop();
  }

}

