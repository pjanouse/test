package org.jboss.qa.hornetq.apps.servlets;

import javax.annotation.Resource;
import javax.jms.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KillerServlet extends HttpServlet {

    // Logger
    private static final Logger log = Logger.getLogger(KillerServlet.class.getName());

    /**
     * @see {@link HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)}
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * @see {@link HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)}
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }


    /**
     * Sends messages into the defined queue
     * @param connection
     * @param queueIn
     * @param out
     * @param count
     * @throws Exception
     */
    protected static void send(Connection connection, Queue queueIn, PrintWriter out, Integer count) throws Exception {
        Session session = null;
        MessageProducer sender = null;
        final String correlation = String.valueOf(System.currentTimeMillis());
        try {
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            sender = session.createProducer(queueIn);
            sender.setDeliveryMode(DeliveryMode.PERSISTENT);
            for (int i = 0; i < count; i++) {
                StringBuilder messageOut = new StringBuilder("XARecovery");
                messageOut.append(">>>");
                for (int size = 0; size < Math.random() * 512; size++) {
                    messageOut.append("1234567890");
                }
                messageOut.append("<<<");
                if (i == (count - 1)) {
                    messageOut.append(" Last Message.");
                }
                messageOut.append(" #" + correlation + " $" + i);
                TextMessage msg = session.createTextMessage(messageOut.toString());
                msg.setLongProperty("messageSize", messageOut.length());
                msg.setIntProperty("messageIndex", i);
                sender.send(msg);
                log.info("Message count: " + i + " was send");
            }
        } finally {
            try {
                if (sender != null)
                    try {
                        sender.close();
                    } catch (Exception e) {
                        log.log(Level.SEVERE, e.getMessage(), e);
                    }
            } finally {
                if (session != null) {
                    try {
                     session.close();
                    } catch (Exception e) {
                        log.log(Level.SEVERE, e.getMessage(), e);
                    }
                }
            }
        }
    }

    
    

    /**
     * Process requests
     * @param request
     * @param response
     * @param queueIn
     * @param queueOut
     * @throws ServletException
     * @throws IOException
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        PrintWriter out = response.getWriter();
        String op = request.getParameter("op");
        
        try {
            
            if (op != null) {
                if (op.equals("kill")) {
                    killServer();
                } else if (op.equals("ping")) {
                    throw new UnsupportedOperationException("Operation: " + op + " is not supoported.");
                }
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, e.getMessage(), e);
            throw new IOException(e);
        } finally {
            out.close();
        }
    }

    private void killServer() throws IOException {
        
        String jvmName = ManagementFactory.getRuntimeMXBean().getName();
        int index = jvmName.indexOf('@');

        if (index < 1) {
            // part before '@' empty (index = 0) / '@' not found (index = -1)
            throw new java.lang.IllegalStateException("Cannot get pid of the process:" + jvmName);
        }

        String pid = null;
        try {
            pid = Long.toString(Long.parseLong(jvmName.substring(0, index)));
        } catch (NumberFormatException e) {
            // ignore
        }
        log.info("pid of the proccess is : " + pid);
        Runtime.getRuntime().exec("kill -9 " + pid);
    }
}