package org.jboss.qa.hornetq.apps.servlets;

import org.jboss.qa.hornetq.HornetQTestCaseConstants;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;

import javax.annotation.Resource;
import javax.jms.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.IllegalStateException;
import java.util.Enumeration;

import org.apache.log4j.Logger;

/**
 * Servlet which sends/receives messages to queue/topic.
 */
public class JmsServlet extends HttpServlet {

    @Resource(mappedName = "java:/ConnectionFactory")
    private ConnectionFactory cf;

    @Resource(mappedName = "jms/queue/InQueue")
    private Queue inQueue;

    public static final String SEND_FILE_FOR_INQUEUE_NAME= "sent-messages-InQueue.txt";

    @Resource(mappedName = "jms/topic/InTopic")
    private Topic inTopic;

    public static final String SEND_FILE_FOR_INTOPIC_NAME = "sent-messages-InTopic.txt";

    @Resource(mappedName = "jms/queue/OutQueue")
    private Queue outQueue;

    public static final String RECEIVE_FILE_FOR_OUTQUEUE_NAME = "received-messages-OutQueue.txt";

    PrintWriter out;

    String basePath = System.getProperty("jboss.home.dir") + File.separator + "bin";

    // Logger
    private static final Logger log = Logger.getLogger(JmsServlet.class.getName());

    /**
     * @param request
     * @param response
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     * @see {@link HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)}
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     * @see {@link HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)}
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }

    /**
     * Process requests
     *
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        out = response.getWriter();
        String op = request.getParameter("op");
//        out.println("op is: " + op);
        try {

            if (op != null) {
                if (op.equals("send")) {
                    int numberOfMessages = Integer.valueOf(request.getParameter("numberOfMessages"));
                    String typeOfMessage = request.getParameter("typeOfMessages");
                    String typeOfDestination = request.getParameter("typeOfDestination");

                    sendMessages(numberOfMessages, typeOfMessage, typeOfDestination);

                } else if (op.equals("receive")) {
                    long receiveTimeout = Long.valueOf(request.getParameter("receiveTimeout"));

                    receiveMessages(receiveTimeout);
                } else if (op.equals("countMessages")) {

                    String countToExit =request.getParameter("countToExit");

                    countMessages(countToExit);

                } else {
                    out.println("Operation: " + op + " is not supoported.");
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new IOException(e);
        } finally {
            out.close();
        }

    }

    private void receiveMessages(long receiveTimeout) throws Exception {
        Connection con = null;
        try {
            con = cf.createConnection();

            Session session = con.createSession(true, Session.SESSION_TRANSACTED);

            con.start();

            MessageConsumer consumer = session.createConsumer(outQueue);

            Message msg;

            int counter = 0;

            StringBuilder contentOfReceiveFile = new StringBuilder();

            while ((msg = consumer.receive(receiveTimeout)) != null) {

                counter++;

                contentOfReceiveFile.append(msg.getStringProperty("inMessageId") + "\n");

                out.println("RECEIVED - " + counter + " - message:" + msg.toString());
                log.info("RECEIVED - " + counter + " +message: " + msg.toString());

                if (counter % 100 == 0) {
                    session.commit();
                }
            }

            session.commit();

            File receiveMessageFile = new File(basePath, RECEIVE_FILE_FOR_OUTQUEUE_NAME);

            if (receiveMessageFile.exists())    {
                receiveMessageFile.delete();
            }

            BufferedWriter output = new BufferedWriter(new FileWriter(receiveMessageFile));
            output.write(contentOfReceiveFile.toString());
            output.close();

            log.info("Print received IDs to file: " + receiveMessageFile.getAbsolutePath());

            out.println("Number of received messages: " + counter);
//            log.info("Number of received messages: " + counter);

        } finally {
            if (con != null) {
                con.close();
            }
        }
    }

    private void countMessages(String countToExit) throws Exception {
        Connection con = null;
        try {
            con = cf.createConnection();

            con.start();

            Session session = con.createSession(true, Session.SESSION_TRANSACTED);

            MessageConsumer consumer = session.createConsumer(outQueue);

            Message msg;

            int counter = 0;

            while ((msg = consumer.receive(1000)) != null) {

                counter++;

                // we want to just count messages and return quickly so if it's receiving too long then return
                if (countToExit != null && counter > Long.valueOf(countToExit))    {
                    break;
                }
            }

            session.rollback();

            out.print(counter);

            log.info("Count messages: " + counter);

        } finally {
            if (con != null) {
                con.close();
            }
        }
    }

    private void sendMessages(int numberOfMessagesToSend, String typeOfMessages, String typeOfDestination) throws Exception {

        Connection con = null;

        try {
            con = cf.createConnection();

            Session session = con.createSession(true, Session.SESSION_TRANSACTED);

            File outPutFile;

            MessageProducer producer;
            if (HornetQTestCaseConstants.TOPIC_DESTINATION_TYPE.equals(typeOfDestination))  {

                producer = session.createProducer(inTopic);

                outPutFile = new File(basePath, SEND_FILE_FOR_INTOPIC_NAME);

            }   else {

                producer = session.createProducer(inQueue);

                outPutFile = new File(basePath, SEND_FILE_FOR_INQUEUE_NAME);

            }

            if (outPutFile.exists())    {
                outPutFile.delete();
            }

            Message msg;

            int counter = 0;

            MessageBuilder messageBuilder = getMessageBuilder(typeOfMessages);

            StringBuilder contentOfSendFile = new StringBuilder();

            while (counter < numberOfMessagesToSend) {

                msg = messageBuilder.createMessage(session);

                msg.setIntProperty("count", counter);

                producer.send(msg);

                // put id to string builder
                contentOfSendFile.append(msg.getJMSMessageID() + "\n");

                counter++;

                out.println("SENT - " + counter + " - message:" + msg.toString());
//                log.info("SENT - " + counter + " - message: " + msg.toString());

                if (counter % 100 == 0) {

                    session.commit();

                }
            }

            session.commit();

            BufferedWriter output = new BufferedWriter(new FileWriter(outPutFile));
            output.write(contentOfSendFile.toString());
            output.close();

            log.info("Print sent IDs to file: " + outPutFile.getAbsolutePath());

        } finally {
            if (con != null) {
                con.close();
            }
        }
    }


    private MessageBuilder getMessageBuilder(String typeOfMessages) {

        MessageBuilder messageBuilder;

        if (HornetQTestCaseConstants.SMALL_MESSAGES.equals(typeOfMessages)) {
            messageBuilder = new ClientMixMessageBuilder(10, 10);
        } else if (HornetQTestCaseConstants.LARGE_MESSAGES.equals(typeOfMessages)) {
            messageBuilder = new ClientMixMessageBuilder(100, 100);
        } else if (HornetQTestCaseConstants.MIXED_MESSAGES.equals(typeOfMessages)) {
            messageBuilder = new ClientMixMessageBuilder(10, 100);
        } else {
            throw new IllegalStateException("Invalid type of messages. Current: " + typeOfMessages + " Valid options are: " +
                HornetQTestCaseConstants.SMALL_MESSAGES + " , " + HornetQTestCaseConstants.LARGE_MESSAGES +
                ", " + HornetQTestCaseConstants.MIXED_MESSAGES);
        }

        return messageBuilder;
    }
}
