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

package net.cfoster.sparrow.impl;

import net.cfoster.sparrow.HttpClient;
import net.cfoster.sparrow.HttpResponseParser;
import net.cfoster.sparrow.HttpResponseHandler;

import java.nio.channels.SocketChannel;
import java.util.Map;

public class HttpClientInspector
{
  private final HttpClient client;

  public HttpClientInspector(HttpClient client)
  {
    this.client = client;
  }

  public SocketChannel socketChannelFor(HttpResponseHandler handler)
  {
    Map<SocketChannel, HttpResponseParser> map = client.httpParsers();

    for(SocketChannel channel : map.keySet())
    {
      HttpResponseParser parser = map.get(channel);
      HttpResponseHandler __handler = parser.getHttpResponseHandler();
      if(__handler == handler)
        return channel;
    }

    throw new RuntimeException(
      "No known SocketChannel for HttpResponseHandler " + handler
    );
  }
}
