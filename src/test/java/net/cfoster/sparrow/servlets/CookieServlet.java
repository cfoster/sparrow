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

package net.cfoster.sparrow.servlets;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class CookieServlet extends HelloWorld
{
  @Override
  protected void doGet(
    HttpServletRequest request,
    HttpServletResponse response) throws ServletException, IOException {

    super.doGet(request, response);

    response.addCookie(new Cookie("single", "value"));
    response.addCookie(new Cookie("multiple", "value1"));
    response.addCookie(new Cookie("multiple", "value2"));
    response.addCookie(new Cookie("multiple", "value3"));


  }
}
