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

import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.logging.Level;

public class ConnectionPool
{
  private final long DEFAULT_TIME_TO_LIVE_SECONDS = 5;

  /** SocketChannel instances that are currently in-use **/
  private final LinkedList<SocketChannel> activeConnections
    = new LinkedList<SocketChannel>();

  /** SocketChannel instances that are not being used **/
  private final LinkedList<SocketChannelContainer> pooledConnections =
    new LinkedList<SocketChannelContainer>();

  private final HttpClient parent;

  public ConnectionPool(HttpClient parent)
  {
    this.parent = parent;
  }

  public void pool(SocketChannel socketChannel, long expiryTime)
  {
    if(HttpClient.log.isLoggable(Level.INFO))
      HttpClient.log.info(this+".pool("+socketChannel+")");

    long expiryTimeMs =
      System.currentTimeMillis() +
      (expiryTime == -1l ? DEFAULT_TIME_TO_LIVE_SECONDS : expiryTime) * 1000l;


    synchronized(pooledConnections) {
      pooledConnections.addLast(
        new SocketChannelContainer(
          socketChannel,
          expiryTimeMs)
      );
      pooledConnections.notifyAll();
    }
    synchronized(activeConnections) {
      activeConnections.remove(socketChannel);
    }
  }

  public void pool(SocketChannel socketChannel)
  {
    pool(socketChannel,
      DEFAULT_TIME_TO_LIVE_SECONDS * 1000l + System.currentTimeMillis());
  }

  public int getPooledConnectionsCount() {
    return pooledConnections.size();
  }

  public int getActiveConnectionsCount() {
    return activeConnections.size();
  }

  public void registerActiveConnection(SocketChannel socketChannel) {
    synchronized(activeConnections) {
      activeConnections.addLast(socketChannel);
    }
  }

  public void deregisterActiveConnection(SocketChannel socketChannel) {
    synchronized(activeConnections) {
      activeConnections.remove(socketChannel);
    }
  }


  public SocketChannel getConnection()
  {
    synchronized(pooledConnections)
    {
      if(activeConnections.size() >= 1024) {
        long t1 = System.currentTimeMillis();
        try { pooledConnections.wait(); } catch(InterruptedException e) { }
        long t2 = System.currentTimeMillis();
        // System.out.println((t2-t1+" ms"));
      }

      while(!pooledConnections.isEmpty())
      {
        SocketChannelContainer socketChannelContainer =
          pooledConnections.removeFirst();

        SocketChannel socketChannel = socketChannelContainer.get();

        if(socketChannel != null) // hasn't expired
        {
          if(socketChannel.isConnected())
          {
            synchronized(activeConnections) {
              activeConnections.addLast(socketChannel);
            }
            return socketChannel;
          }
          else
          {
            parent.socketDiedEvent(socketChannel);
          }

        }
      }
    }

    return null;
  }

  private class SocketChannelContainer {
    private final SocketChannel socketChannel;
    final long expiryTime;

    public SocketChannelContainer(
      SocketChannel socketChannel,
      long expiryTime) {
      this.socketChannel = socketChannel;
      this.expiryTime = expiryTime;
    }

    public SocketChannel get() {
      if(System.currentTimeMillis() < expiryTime)
        return socketChannel;
      else
        return null;
    }
  }

  public String toString()
  {
    return "[ConnectionPool: activeConnection="+
      activeConnections+", pooledConnections="+pooledConnections+"]";
  }
}
