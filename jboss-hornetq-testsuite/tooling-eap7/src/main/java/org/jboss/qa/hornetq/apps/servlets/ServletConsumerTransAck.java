package org.jboss.qa.hornetq.apps.servlets;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.constants.Constants;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TransactionRolledBackException;
import javax.naming.Context;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by okalman on 8/10/15.
 */
@WebServlet("/ServletConsumerTransAck")
public class ServletConsumerTransAck extends HttpServlet{
    private static final Logger log = Logger.getLogger(ServletConsumerTransAck.class.getName());
    int commitAfter = 1;
    String queueJNDIName = "jms/queue/targetQueue0";
    String host = "127.0.0.1";
    int port = 8080;
    String protocol = "http-remoting";
    long receiveTimeOut = 10000;
    int counter = 0;
    int commitCounter=0;
    private static final int MAX_RETRIES = 20;
    List<Message> messagesToCommit = new ArrayList<Message>();
    private static List<Map<String,String>> listOfReceivedMessages = new ArrayList<Map<String,String>>();
    private Set<Message> setOfReceivedMessagesWithPossibleDuplicates = new HashSet<Message>();
    private Set<Message> setOfReceivedMessagesWithPossibleDuplicatesForLaterDuplicateDetection = new HashSet<Message>();
    private static int resultCount=0;
    private static String exceptions="";




    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req,resp);
    }

    @Override
    protected  void doPost(HttpServletRequest req, HttpServletResponse resp)  throws ServletException, IOException{
        if(req.getParameter(ServletConstants.PARAM_METHOD)!=null && req.getParameter(ServletConstants.PARAM_METHOD).equals(ServletConstants.METHOD_GET_COUNT)){
            resp.setContentType("text;charset=UTF-8");
            PrintWriter writer = resp.getWriter();
            writer.println(resultCount);
            writer.close();
            return;
        }
        if(req.getParameter(ServletConstants.PARAM_METHOD)!=null && req.getParameter(ServletConstants.PARAM_METHOD).equals(ServletConstants.METHOD_GET_EXCEPTIONS)){
            resp.setContentType("text;charset=UTF-8");
            PrintWriter writer = resp.getWriter();
            writer.println(exceptions);
            writer.close();
            return;
        }
        if(req.getParameter(ServletConstants.PARAM_METHOD)!=null && req.getParameter(ServletConstants.PARAM_METHOD).equals(ServletConstants.METHOD_GET_MESSAGES)){
            resp.setContentType("text;charset=UTF-8");
            PrintWriter writer = resp.getWriter();
            writer.println(new ObjectMapper().writeValueAsString(listOfReceivedMessages));
            writer.close();
            return;
        }
        try {
            commitAfter = (req.getParameter(ServletConstants.PARAM_COMMIT_AFTER) != null) ? Integer.parseInt(req.getParameter(ServletConstants.PARAM_COMMIT_AFTER)) : 1;
        }catch (NumberFormatException e){
            log.error(e.toString());
        }
        try{
            port = (req.getParameter(ServletConstants.PARAM_PORT) != null) ? Integer.parseInt(req.getParameter(ServletConstants.PARAM_PORT)) : 8080;
        }catch (NumberFormatException e){
            log.error(e.toString());
        }
        try{
            receiveTimeOut = (req.getParameter(ServletConstants.PARAM_RECEIVE_TIMEOUT) != null) ? Integer.parseInt(req.getParameter(ServletConstants.PARAM_RECEIVE_TIMEOUT)) : 10000;
        }catch (NumberFormatException e){
            log.error(e.toString());
        }
        queueJNDIName = (req.getParameter(ServletConstants.PARAM_QUEUE_JNDI_NAME) !=null) ? req.getParameter(ServletConstants.PARAM_QUEUE_JNDI_NAME) : "jms/queue/targetQueue0";
        host = (req.getParameter(ServletConstants.PARAM_HOST)!=null) ? req.getParameter(ServletConstants.PARAM_HOST) : "127.0.0.1";
        protocol = (req.getParameter(ServletConstants.PARAM_PROTOCOL) != null) ? req.getParameter(ServletConstants.PARAM_PROTOCOL) : "http-remoting";


        resp.setContentType("text;charset=UTF-8");
        PrintWriter writer = resp.getWriter();
        writer.println("RUNNING");
        writer.close();
        try {
            receiveMessages();
        }catch (Exception e){
            exceptions = exceptions +e.toString()+"\n";
            log.error(e.toString());
        }

    }

    private void receiveMessages() throws Exception{
        Context context = JMSTools.getEAP7Context(host, port);
        Connection connection = null;
        try {
            ConnectionFactory cf = (ConnectionFactory) context.lookup(Constants.CONNECTION_FACTORY_JNDI_EAP7);
            connection = cf.createConnection();
            Queue queue = (Queue) context.lookup(queueJNDIName);
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            MessageConsumer consumer = session.createConsumer(queue);
            connection.start();
            Message message = null;
            while ((message = receiveMessage(consumer)) !=null) {
                messagesToCommit.add(message);
                counter++;
                if ((counter % commitAfter) == 0) {
                    if((commitCounter % 2) == 0 && commitCounter > 0){
                        session.rollback();
                        log.info("Receiver for node: " + host + ". Received message - count: "
                                + counter + " SENT ROLLBACK");
                        counter-=commitAfter;
                        messagesToCommit.clear();
                    }else{
                        commitSession(session);
                    }
                    commitCounter++;
                }
                resultCount=counter;
            }
            session.commit();
            resultCount=counter;
            log.info("Received: " + counter);

        }catch (Exception e){
            log.error(e.toString());

        }finally {
            if(connection != null){
                connection.close();
            }
        }
    }

    public Message receiveMessage(MessageConsumer consumer) throws Exception {

        Message msg = null;
        int numberOfRetries = 0;

        // receive message with retry
        while (numberOfRetries < MAX_RETRIES) {

            try {

                msg = consumer.receive(receiveTimeOut);
                if (msg != null) {
                    msg = cleanMessage(msg);
                }
                return msg;

            } catch (JMSException ex) {
                numberOfRetries++;
                log.error("RETRY receive for host: " + host + ", Trying to receive message with count: " + (counter + 1), ex);
            }
        }

        throw new Exception("FAILURE - MaxRetry reached for receiver for node: " + host);
    }

    private Message cleanMessage(Message m) throws JMSException {

        String dupId = m.getStringProperty("_HQ_DUPL_ID");
        String inMessageId = m.getStringProperty("inMessageId");
        String JMSXGroupID = m.getStringProperty("JMSXGroupID");
        m.clearBody();
        m.clearProperties();
        m.setStringProperty("_HQ_DUPL_ID", dupId);
        m.setStringProperty("inMessageId", inMessageId);
        if (JMSXGroupID != null) {
            m.setStringProperty("JMSXGroupID", JMSXGroupID);
        }
        return m;
    }


    public void commitSession(Session session) throws Exception {

        int numberOfRetries = 0;

        while (numberOfRetries < MAX_RETRIES) {
            try {

                areThereDuplicatesInLaterDetection();

                session.commit();

                log.info("Receiver for node: " + host + ". Received message - count: "
                        + counter + " SENT COMMIT");

                addMessages(listOfReceivedMessages, messagesToCommit);
                StringBuilder stringBuilder = new StringBuilder();
                for (Message m : messagesToCommit) {
                    stringBuilder.append("messageId: ").append(m.getJMSMessageID()).append(" dupId: ").append(m.getStringProperty("_HQ_DUPL_ID" + "\n"));
                }
                log.debug("Adding messages: " + stringBuilder.toString());

                return;

            } catch (TransactionRolledBackException ex) {
                log.error(" Receiver - COMMIT FAILED - TransactionRolledBackException thrown during commit: " + ex.getMessage() + ". Receiver for node: " + host
                        + ". Received message - count: " + counter + ", retrying receive", ex);
                // all unacknowledge messges will be received again
                ex.printStackTrace();
                counter = counter - messagesToCommit.size();
                setOfReceivedMessagesWithPossibleDuplicates.clear();

                return;

            } catch (JMSException ex) {
                // we need to know which messages we got in the first try because we need to detect possible duplicates
//                setOfReceivedMessagesWithPossibleDuplicates.addAll(listOfReceivedMessagesToBeCommited);
                setOfReceivedMessagesWithPossibleDuplicatesForLaterDuplicateDetection.addAll(messagesToCommit);

                addMessages(listOfReceivedMessages, messagesToCommit);
                StringBuilder stringBuilder = new StringBuilder();
                for (Message m : messagesToCommit) {
                    stringBuilder.append("messageId: ").append(m.getJMSMessageID()).append(" dupId: ").append(m.getStringProperty("_HQ_DUPL_ID" + "\n"));
                }
                log.debug("Adding messages: " + stringBuilder.toString());

                log.error(" Receiver - JMSException thrown during commit: " + ex.getMessage() + ". Receiver for node: " + host
                        + ". Received message - count: " + counter + ", COMMIT will be tried again - TRY:" + numberOfRetries, ex);
                ex.printStackTrace();
                numberOfRetries++;
            } finally {
                // we clear this list because next time we get new or duplicated messages and we compare it with set possible duplicates
                messagesToCommit.clear();
            }
        }

        throw new Exception("FAILURE - MaxRetry reached for receiver for node: " + host + " during acknowledge");
    }

    private boolean areThereDuplicatesInLaterDetection() throws JMSException {
        boolean isDup = false;

        Set<String> setOfReceivedMessages = new HashSet<String>();
        for (Message m : messagesToCommit) {
            setOfReceivedMessages.add(m.getStringProperty("_HQ_DUPL_ID"));
        }
        StringBuilder foundDuplicates = new StringBuilder();
        for (Message m : setOfReceivedMessagesWithPossibleDuplicatesForLaterDuplicateDetection) {
            if (!setOfReceivedMessages.add(m.getStringProperty("_HQ_DUPL_ID"))) {
                foundDuplicates.append(m.getJMSMessageID());
                counter -= 1;
                // remove this duplicate from the list
                List<Message> iterationList = new ArrayList<Message>(messagesToCommit);
                for (Message receivedMessage : iterationList)    {
                    if (receivedMessage.getStringProperty("_HQ_DUPL_ID").equals(m.getStringProperty("_HQ_DUPL_ID"))) {
                        messagesToCommit.remove(receivedMessage);
                    }
                }

                isDup = true;
            }
        }
        if (!"".equals(foundDuplicates.toString())) {
            log.info("Later detection found duplicates: " + foundDuplicates.toString());
            log.info("List of messages to be added to list: " + messagesToCommit.toString());
        }
        return isDup;
    }
    protected void addMessage(List<Map<String,String>> listOfReceivedMessages, Message message) throws JMSException {
        Map<String, String> mapOfPropertiesOfTheMessage = new HashMap<String,String>();
        mapOfPropertiesOfTheMessage.put("messageId", message.getJMSMessageID());
        if (message.getStringProperty("_HQ_DUPL_ID") != null)   {
            mapOfPropertiesOfTheMessage.put("_HQ_DUPL_ID", message.getStringProperty("_HQ_DUPL_ID"));
        }
        // this is for MDB test versification (MDB creates new message with inMessageId property)
        if (message.getStringProperty("inMessageId") != null)   {
            mapOfPropertiesOfTheMessage.put("inMessageId", message.getStringProperty("inMessageId"));
        }
        if (message.getStringProperty("JMSXGroupID") != null)   {
            mapOfPropertiesOfTheMessage.put("JMSXGroupID", message.getStringProperty("JMSXGroupID"));
        }
        listOfReceivedMessages.add(mapOfPropertiesOfTheMessage);
    }

    protected void addMessages(List<Map<String,String>> listOfReceivedMessages, List<Message> messages) throws JMSException {
        for (Message m : messages)  {
            addMessage(listOfReceivedMessages, m);
        }
    }

}
