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
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;

public class StreamingDataServlet extends HttpServlet
{
  private final long contentSizeBytes;
  private final long bufferSize = 1024 * 1024;

  public StreamingDataServlet(long contentSizeBytes)
  {
   this.contentSizeBytes = contentSizeBytes;
  }

  @Override
  protected void doGet(
    HttpServletRequest request,
    HttpServletResponse response) throws ServletException, IOException
  {
    response.setContentType("application/octet-stream");
    response.setStatus(HttpServletResponse.SC_OK);

    final byte[] buffer = new byte[(int)bufferSize];

    for(int i=0;i<bufferSize;i++)
      buffer[i] = (byte) (i & 0xFF);

    OutputStream out = response.getOutputStream();

    long totalWritten=0;

    while(totalWritten < contentSizeBytes)
    {
      long w = Math.min(bufferSize, contentSizeBytes - totalWritten);
      out.write(buffer, 0, (int)w);
      out.flush();
      totalWritten += w;
    }

    out.flush();
  }
}
