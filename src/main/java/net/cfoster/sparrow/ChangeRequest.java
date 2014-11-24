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

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class ChangeRequest
{
  public static final int REGISTER  = 1;
  public static final int CHANGEOPS = 2;
  public static final int CANCEL    = 3;

  public SocketChannel socket;
  public int type;
  public int ops;

  public ChangeRequest(SocketChannel socket, int type, int ops)
  {
    if(socket == null)
      throw new IllegalArgumentException("SocketChannel can not be null.");
    this.socket = socket;
    this.type = type;
    this.ops = ops;
  }

  public String toString() {
    String typeStr = null;

    if(type == REGISTER)
      typeStr = "REGISTER";
    else if(type == CHANGEOPS)
      typeStr = "CHANGEOPS";
    else if(type == CANCEL)
      typeStr = "CANCEL";

    String opsStr = null;

    if(ops == SelectionKey.OP_ACCEPT)
      opsStr = "OP_ACCEPT";
    else if(ops == SelectionKey.OP_CONNECT)
      opsStr = "OP_CONNECT";
    else if(ops == SelectionKey.OP_READ)
      opsStr = "OP_READ";
    else if(ops == SelectionKey.OP_WRITE)
      opsStr = "OP_WRITE";
    else
      opsStr = "OP_COMBINED";


    return "[ChangeRequest: type=" +typeStr
      +
      ", ops="+opsStr+", socket="+socket+"]";
  }
}
