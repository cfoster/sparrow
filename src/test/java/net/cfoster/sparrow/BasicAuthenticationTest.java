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
import net.cfoster.sparrow.servlets.HelloWorld;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;

import static org.junit.Assert.assertEquals;

public class BasicAuthenticationTest
{
  private static final int SERVER_PORT = 8910;
  private Server server = null;

  private static final String user = "scott";
  private static final String pass = "tiger";
  private static final String realm = "Private!";

  @Test
  public void testBasicAuth()
  {
    HttpClient client = HttpClient.getInstance();

    HttpRequest req = new HttpRequest("/".getBytes(), "localhost".getBytes());
    req.setUser(user.getBytes());
    req.setPassword(pass.getBytes());

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

  @Before
  public void startJettyServer() throws Exception
  {
    server = new Server(SERVER_PORT);

    ServletContextHandler context = new ServletContextHandler(
      ServletContextHandler.SESSIONS
    );

    context.setSecurityHandler(basicAuth(user, pass, realm));
    context.setContextPath("/");
    server.setHandler(context);
    context.addServlet(new ServletHolder(new HelloWorld()),"/*");
    server.start();
  }

  @After
  public void stopJettyServer() throws Exception {
    server.stop();
  }

  private static final SecurityHandler basicAuth(
    String username,String password, String realm)
  {
    HashLoginService l = new HashLoginService();

    l.putUser(
      username,
      Credential.getCredential(password),
      new String[] {"user"}
    );

    l.setName(realm);

    Constraint constraint = new Constraint();
    constraint.setName(Constraint.__BASIC_AUTH);
    constraint.setRoles(new String[]{"user"});
    constraint.setAuthenticate(true);

    ConstraintMapping cm = new ConstraintMapping();
    cm.setConstraint(constraint);
    cm.setPathSpec("/*");

    ConstraintSecurityHandler csh = new ConstraintSecurityHandler();
    csh.setAuthenticator(new BasicAuthenticator());
    csh.setRealmName("myrealm");
    csh.addConstraintMapping(cm);
    csh.setLoginService(l);

    return csh;
  }
}
