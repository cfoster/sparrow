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

import net.cfoster.sparrow.helpers.VerboseHandler;
import net.cfoster.sparrow.HttpClient;
import net.cfoster.sparrow.HttpRequest;

import java.net.InetSocketAddress;

public class WikiDump
{
  static String host = "dumps.wikimedia.org";
  static String uri = "/enwiki/20140102/enwiki-20140102-pages-meta-history8.xml-p000547644p000575345.bz2";
  // static String uri = "/enwiki/20140102/enwiki-20140102-pages-meta-history8.xml-p000663705p000665000.7z";

  static int port = 80;

  public static void main(String[] args) throws Exception
  {
    HttpClient client = HttpClient.getInstance();
    HttpRequest req = new HttpRequest(uri.getBytes(), host.getBytes());
    VerboseHandler handler = new VerboseHandler(60 * 60);

    client.send(
      req,
      new InetSocketAddress(host, port),
      handler
    );

    System.out.println("sent request");

    handler.waitForCompleteOrFailOrTimeOut();
    long total = handler.getTotalBytesReceived();

    System.out.println("got "+total);
  }

}

