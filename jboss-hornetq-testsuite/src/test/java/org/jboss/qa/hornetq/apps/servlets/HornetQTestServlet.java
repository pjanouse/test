package org.jboss.qa.hornetq.apps.servlets;

import javax.annotation.Resource;
import javax.jms.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HornetQTestServlet extends HttpServlet {

    // Logger
    private static final Logger log = Logger.getLogger(HornetQTestServlet.class.getName());

    @Resource(mappedName = "java:/ConnectionFactory")
    private ConnectionFactory connectionFactory;

    @Resource(mappedName = "java:/queue/InQueue")
    private Queue queueIn;

    @Resource(mappedName = "java:/queue/OutQueue")
    private Queue queueOut;

    /**
     * @see {@link HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)}
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response, queueIn, queueOut);
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
     * Drains queue
     * @param connection
     * @param queue
     * @param timeout
     * @return
     */
    private static long drainQueue(Connection connection, Queue queue, long timeout, PrintWriter out, boolean writeInfo) {
        log.info("Starting with draining queue " + queue + " timeout " + timeout + " ms");
        Session session = null;
        MessageConsumer receiver = null;
        Message msg;
        Map<Integer, Integer> receivedMessages = new HashMap<Integer, Integer>();
        long counter = 0;
        try {
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            receiver = session.createConsumer(queue);
            if (writeInfo) {
                out.println("Draining queue " + queue);
                out.println("=================================");
            }
            do {
                msg = receiver.receive(timeout);
                if (log.isLoggable(Level.FINE)) {
                    log.log(Level.FINE, "Drained message :" + msg);
                }
                if (writeInfo) {
                    out.println("Drained message " + msg);
                }
                if (msg!=null) {
                    counter++;
                    int messageIndex = msg.getIntProperty("messageIndex");
                    if (receivedMessages.containsKey(messageIndex)) {
                        int msgcounter = receivedMessages.get(messageIndex);
                        msgcounter++;
                        receivedMessages.put(messageIndex, msgcounter);
                    } else {
                        receivedMessages.put(messageIndex, 1);
                    }
                }
            } while (msg != null);
            if (writeInfo) {
                out.println("=================================");
                out.println("Drained messages " + counter);
                StringBuffer sb = new StringBuffer();
                sb.append("Received " + receivedMessages.keySet().size() + " messages with unique messageIndex\n");
                for (int i : receivedMessages.keySet()) {
                    if (receivedMessages.get(i)!=1) {
                        sb.append("  Message with index " + i + " has been delivered " + receivedMessages.get(i));
                    }
                }
                out.print(sb.toString());
                log.info(sb.toString());
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, e.getMessage(), e);
        } finally {
            if (receiver!=null) {
                try {
                    receiver.close();
                } catch (Exception e) {
                    log.log(Level.SEVERE, e.getMessage(), e);
                }
            }
            if (session!=null) {
                try {
                    session.close();
                } catch (Exception e) {
                    log.log(Level.SEVERE, e.getMessage(), e);
                }
            }
        }
        log.info("End of draining queue " + queue + " drained " + counter + " messages");
        return counter;
    }

    /**
     * Drain queues
     * @param connection
     * @param queueIn
     * @param queueOut
     * @param out
     * @throws Exception
     */
    protected static void drainQueues(Connection connection, Queue queueIn, Queue queueOut, PrintWriter out) {
        long inQueueCount = drainQueue(connection, queueIn, 1000, out, true);
        long outQueueCount = drainQueue(connection, queueOut, 1000, out, true);
        log.log(Level.INFO, "Drained queue " + queueIn + " messages = " + inQueueCount);
        log.log(Level.INFO, "Drained queue " + queueOut + " messages = " + outQueueCount);
    }

    /**
     * Get count of messages
     * @param connection
     * @param out
     * @throws Exception
     */
    protected static void getCountOfMessages(Connection connection, Queue queue, PrintWriter out) {
        long count = drainQueue(connection, queue, 1000, out, false);
        log.log(Level.INFO, "Count of messages on queue " + queue + " messages = " + count);
        out.println(count);
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
    protected void processRequest(HttpServletRequest request, HttpServletResponse response, Queue queueIn,
                                         Queue queueOut)
            throws ServletException, IOException {

        PrintWriter out = response.getWriter();
        String op = request.getParameter("op");
        Connection connection = null;

        try {
            connection = connectionFactory.createConnection();
            connection.start();
            if (op != null) {
                if (op.equals("send")) {
                    Integer count = Integer.parseInt(request.getParameter("count"));
                    send(connection, queueIn, out, count);
                } else if (op.equals("get-count-out")) {
                    getCountOfMessages(connection, queueOut, out);
                } else if (op.equals("get-count-in")) {
                    getCountOfMessages(connection, queueIn, out);
                } else if (op.equals("drain-queues")) {
                    drainQueues(connection, queueIn, queueOut, out);
                } else if (op.equals("ping")) {
                    out.println("pong");
                }
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, e.getMessage(), e);
            throw new IOException(e);
        } finally {
            if (connection!=null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    log.severe(e.getMessage());
                }
            }
            out.close();
        }
    }
}