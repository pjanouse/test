/*
 * JBoss, the OpenSource J2EE webOS
 * 
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.qa.tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jboss.qa.tools.ControllableProxy;

/**
 * A SimpleProxyServer.
 * <p/>
 * Implementation of the simple one threaded proxy which allows
 * to debug and block communication on the demand.
 * 
 * @author <a href="pslavice@jboss.com">Pavel Slavicek</a>
 * @version $Revision: 1.1 $
 */
public class SimpleProxyServer implements ControllableProxy
{
   // Logger
   private static final Logger log = Logger.getLogger(SimpleProxyServer.class.getName());

   // Target host
   private String host;

   // Remote port
   private int remotePort;

   // Local port where client will connect
   private int localPort;

   // Is there request for termination?
   private volatile boolean terminateRequest = false;

   // Main thread was terminated?
   private volatile boolean terminated = true;

   // Debug all communication?
   private boolean debugCommunication;

   // Block communication to server?
   private volatile boolean blockCommToServer;

   // Block communication to client?
   private volatile boolean blockCommToClient;

   // Maximal size of content for debug output
   private int maxSizeOfDebugContent = 100;

   /**
    * Create a new SimpleProxyServer.
    * 
    * @param host
    * @param remotePort
    * @param localPort
    */
   public SimpleProxyServer(String host, int remotePort, int localPort)
   {
      this(host, remotePort, localPort, false);
   }

   /**
    * Create a new SimpleProxyServer.
    * 
    * @param host
    * @param remotePort
    * @param localPort
    * @param debugCommunication
    */
   
   public SimpleProxyServer(String host, int remotePort, int localPort,
         boolean debugCommunication)
   {
      this.host = host;
      this.remotePort = remotePort;
      this.localPort = localPort;
      this.debugCommunication = debugCommunication;
   }

   /**
    * Start proxy
    */
   public void start()
   {
      if (log.isLoggable(Level.INFO))
      {
         log.log(Level.INFO, "Starting proxy " + this);
      }
      if (this.terminated)
      {
         this.terminateRequest = false;
         new Thread(new Runnable()
         {
            public void run()
            {
               runServer();
            }
         }, "ProxyServer").start();
      }
   }

   /** 
    * Stop proxy
    */
   public void stop()
   {
      if (log.isLoggable(Level.INFO))
      {
         log.log(Level.INFO, "Stopping proxy " + this);
      }
      this.terminateRequest = true;
      while (!this.terminated)
      {
         try
         {
            Thread.yield();
         }
         catch (Exception e)
         {
         }
      }
   }

   /**
    * Method runs thread with proxy which waits for connection
    *
    */
   private void runServer()
   {
      ServerSocket ss = null;
      try
      {
         ss = new ServerSocket(this.localPort);

         while (!this.terminateRequest)
         {
            ss.setSoTimeout(500);
            try
            {
               new ProxyThread(ss.accept(), this.host, this.remotePort, this,
                     this.debugCommunication).start();
            }
            catch (SocketTimeoutException ex)
            {
               // Just ignore this exception
            }
         }
      }
      catch (Exception e)
      {
         log.log(Level.SEVERE, e.getMessage(), e);
      }
      finally
      {
         if (ss != null)
         {
            try
            {
               ss.close();
            }
            catch (IOException e)
            {
               log.log(Level.SEVERE, e.getMessage(), e);
            }
         }
         this.terminated = true;
      }
   }

   /**
    * Prints content of the message
    * 
    * @param data
    * @param type
    */
   public void debug(byte[] data, String type)
   {
      String tmp = new String(data);
      log.log(Level.FINE, "");
      log.log(Level.FINE, "*** " + type + " ***");
      log.log(Level.FINE, hexView(tmp.substring(0, Math.min(this.maxSizeOfDebugContent, tmp.length()))));
   }

   /**
    * Formats content of the message
    * 
    * @param tmp
    * @return
    */
   protected static String hexView(String tmp)
   {
      if (tmp == null)
      {
         return "";
      }
      StringBuilder result = new StringBuilder();
      StringBuilder hex = new StringBuilder();
      StringBuilder output = new StringBuilder();
      for (int i = 0; i < tmp.length(); i++)
      {
         if (i % 16 == 0 && i != 0)
         {
            if (result.length() > 0)
            {
               result.append("\n");
            }
            result.append(hex.toString());
            result.append("    ");
            result.append(output.toString());
            hex = new StringBuilder();
            output = new StringBuilder();
         }
         else
         {
            if (hex.length() > 0)
            {
               hex.append((i % 8 == 0) ? " | " : " ");
            }
         }
         char ch = tmp.charAt(i);
         output.append(isStringChar(ch) ? ch : ".");
         String hexCode = Integer.toHexString((int) ch);
         while (hexCode.length() < 4)
         {
            hexCode = "0" + hexCode;
         }
         hex.append(hexCode);
      }
      if (hex.length() > 0)
      {
         if (result.length() > 0)
         {
            result.append("\n");
         }
         while (hex.length() < 85)
         {
            hex.append(" ");
         }
         result.append(hex.toString());
         result.append(output.toString());
      }
      return result.toString();
   }

   /**
    * Returns <code>true</code> if the given
    * char is printable.
    * 
    * @param ch
    * @return
    */
   protected static boolean isStringChar(char ch)
   {
      if (ch >= 'a' && ch <= 'z')
         return true;
      if (ch >= 'A' && ch <= 'Z')
         return true;
      if (ch >= '0' && ch <= '9')
         return true;
      switch (ch)
      {
         case '/' :
         case '-' :
         case ':' :
         case '.' :
         case ',' :
         case '_' :
         case '$' :
         case '%' :
         case '\'' :
         case '(' :
         case ')' :
         case '[' :
         case ']' :
         case '<' :
         case '>' :
            return true;
      }
      return false;
   }

   @Override
   public void setBlockCommunicationToServer(boolean isBlocked)
   {
      this.blockCommToServer = isBlocked;
   }

   @Override
   public void setBlockCommunicationToClient(boolean isBlocked)
   {
      this.blockCommToClient = isBlocked;
   }

   public void setMaxSizeOfDebugContent(int maxSizeOfDebugContent)
   {
      this.maxSizeOfDebugContent = maxSizeOfDebugContent;
   }

   @Override
   public String toString()
   {
      return this.getClass().getName() + " " + this.localPort + " -> " + this.host + ":" + this.remotePort;
   }

   @Override
   public void setTerminateRequest()
   {
      this.terminateRequest = true;
   }

   @Override
   public boolean isTerminateRequest()
   {
      return this.terminateRequest;
   }

   @Override
   public boolean isBlockCommunicationToServer()
   {
      return this.blockCommToServer;
   }

   @Override
   public boolean isBlockCommunicationToClient()
   {
      return this.blockCommToClient;
   }

   /**
    * Connection thread
    * 
    * @author <a href="pslavice@jboss.com">Pavel Slavicek</a>
    * @version $Revision: 1.1 $
    */
   private static class ProxyThread extends Thread
   {

      private static final int BUFFER_SIZE = 32768;

      private Socket clientSocket = null;

      private Socket serverSocket = null;

      private String host;

      private int remotePort;

      boolean debugCommunication;

      private ControllableProxy controllableProxy;

      /**
       * Create a new ProxyThread.
       * 
       * @param socket
       * @param host
       * @param remotePort
       */
      public ProxyThread(Socket socket, String host, int remotePort, ControllableProxy controllableProxy,
            boolean debugCommunication)
      {
         super("ProxyThread");
         this.clientSocket = socket;
         this.host = host;
         this.remotePort = remotePort;
         this.controllableProxy = controllableProxy;
         this.debugCommunication = debugCommunication;
      }

      /**
       * @see {@link Thread#run()}
       */
      @Override
      public void run()
      {
         final byte[] request = new byte[BUFFER_SIZE];
         byte[] reply = new byte[BUFFER_SIZE];

         if (this.clientSocket == null)
         {
            log.log(Level.SEVERE, "Cannot open connection for null client socket!");
            return;
         }
         try
         {
            final InputStream from_client = clientSocket.getInputStream();
            final OutputStream to_client = clientSocket.getOutputStream();

            try
            {
               serverSocket = new Socket(host, this.remotePort);
            }
            catch (IOException e)
            {
               String msg = "Proxy server cannot connect to " + host + ":" + this.remotePort + ":\n" + e + "\n";
               PrintWriter out = new PrintWriter(to_client);
               out.print(msg);
               out.flush();
               this.clientSocket.close();
               this.controllableProxy.setTerminateRequest();
               log.log(Level.SEVERE, msg);
               return;
            }

            // Get server streams.
            final InputStream from_server = serverSocket.getInputStream();
            final OutputStream to_server = serverSocket.getOutputStream();
            Thread t = new Thread()
            {

               @Override
               public void run()
               {
                  int bytes_read;
                  try
                  {
                     while ((bytes_read = from_client.read(request)) != -1 && !controllableProxy.isTerminateRequest())
                     {
                        
                        if (!controllableProxy.isBlockCommunicationToServer())
                        {
                           to_server.write(request, 0, bytes_read);
                           to_server.flush();
                           if (debugCommunication)
                           {
                              controllableProxy.debug(request, "to server");
                           }
                        }
                     }
                  }
                  catch (IOException e)
                  {
                     log.log(Level.SEVERE, e.getMessage(), e);
                  }

                  try
                  {
                     to_server.close();
                  }
                  catch (IOException e)
                  {
                     log.log(Level.SEVERE, e.getMessage(), e);
                  }
               }
            };
            t.start();

            int bytes_read;
            try
            {
               while ((bytes_read = from_server.read(reply)) != -1)
               {
                  
                  if (!controllableProxy.isBlockCommunicationToClient())
                  {
                     to_client.write(reply, 0, bytes_read);
                     to_client.flush();
                     if (debugCommunication)
                     {
                        controllableProxy.debug(reply, "to client");
                     }
                  }
               }
            }
            catch (IOException e)
            {
               log.log(Level.SEVERE, e.getMessage());
            }

            to_client.close();
         }
         catch (IOException e)
         {
            log.log(Level.SEVERE, e.getMessage(), e);
         }
         finally
         {
            if (serverSocket != null)
            {
               try
               {
                  serverSocket.close();
               }
               catch (Exception e)
               {
                  log.log(Level.SEVERE, e.getMessage(), e);
               }
            }
            if (clientSocket != null)
            {
               try
               {
                  clientSocket.close();
               }
               catch (Exception e)
               {
                  log.log(Level.SEVERE, e.getMessage(), e);
               }
            }
         }
      }
   }
   
   public static void main(String args[])   {
       SimpleProxyServer proxy = new SimpleProxyServer("192.168.1.2", 5445, 56831);
       proxy.start();
       
   }
}