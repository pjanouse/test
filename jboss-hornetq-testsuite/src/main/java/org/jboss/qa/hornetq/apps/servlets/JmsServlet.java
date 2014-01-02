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
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.IllegalStateException;

import org.apache.log4j.Logger;

/**
 * Servlet which sends/receives messages to queue/topic.
 */
public class JmsServlet extends HttpServlet {

    @Resource(mappedName = "java:/JmsXA")
    private ConnectionFactory cf;

    @Resource(mappedName = "jms/queue/InQueue")
    private Queue inQueue;

    @Resource(mappedName = "jms/topic/InTopic")
    private Topic inTopic;

    @Resource(mappedName = "jms/queue/OutQueue")
    private Queue outQueue;

    PrintWriter out;

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

                    countMessages();

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

            while ((msg = consumer.receive(receiveTimeout)) != null) {

                counter++;

                out.println("RECEIVED - " + counter + " - message:" + msg.toString());
                log.info("RECEIVED - " + counter + " +message: " + msg.toString());

                if (counter % 100 == 0) {
                    session.commit();
                }
            }

            session.commit();

            out.println("Number of received messages: " + counter);
            log.info("Number of received messages: " + counter);

        } finally {
            if (con != null) {
                con.close();
            }
        }
    }

    private void countMessages() throws Exception {
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

                if (counter % 100 == 0) {
                    session.commit();
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

            MessageProducer producer;
            if (HornetQTestCaseConstants.TOPIC_DESTINATION_TYPE.equals(typeOfDestination))  {

                producer = session.createProducer(inTopic);

            }   else {

                producer = session.createProducer(inQueue);

            }

            Message msg;

            int counter = 0;

            MessageBuilder messageBuilder = getMessageBuilder(typeOfMessages);

            while (counter < numberOfMessagesToSend) {

                msg = messageBuilder.createMessage(session);

                msg.setIntProperty("count", counter);

                producer.send(msg);

                counter++;

                out.println("SENT - " + counter + " - message:" + msg.toString());
                log.info("SENT - " + counter + " - message: " + msg.toString());

                if (counter % 100 == 0) {

                    session.commit();

                }
            }

            session.commit();

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
