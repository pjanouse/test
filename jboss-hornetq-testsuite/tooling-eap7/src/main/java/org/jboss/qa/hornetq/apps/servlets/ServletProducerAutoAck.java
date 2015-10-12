package org.jboss.qa.hornetq.apps.servlets;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.impl.ArtemisJMSImplementation;
import org.jboss.qa.hornetq.apps.impl.MessageCreator10;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.constants.Constants;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
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
import java.util.List;
import java.util.Map;

/**
 * Created by okalman on 9/3/15.
 */

@WebServlet("/ServletProducer")
public class ServletProducerAutoAck extends HttpServlet {
    private static final Logger log = Logger.getLogger(ServletProducerAutoAck.class.getName());
    int maxMessages=-1;
    String queueJNDIName="jms/queue/targetQueue0";
    String host="127.0.0.1";
    int port=8080;
    String protocol = "http-remoting";
    int counter=0;
    private static List<Map<String,String>> listOfSentMessages = new ArrayList<Map<String,String>>();

    private static final int MAX_RETRIES =20;
    private static String exceptions="";


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req,resp);
    }
    protected  void doPost(HttpServletRequest req, HttpServletResponse resp)  throws ServletException, IOException{
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
            writer.println(new ObjectMapper().writeValueAsString(listOfSentMessages));
            writer.close();
            return;
        }
        try{
            maxMessages = (req.getParameter(ServletConstants.PARAM_MAX_MESSAGES) != null) ? Integer.parseInt(req.getParameter(ServletConstants.PARAM_MAX_MESSAGES)) : -1;
        }catch (NumberFormatException e){
            log.error(e.toString());
        }
        try{
            port = (req.getParameter(ServletConstants.PARAM_PORT) != null) ? Integer.parseInt(req.getParameter(ServletConstants.PARAM_PORT)) : 8080;
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
            sendMessages();
        }catch (Exception e){
            log.error(e.toString());
        }

    }

    private void sendMessages() throws Exception{
        Context context = JMSTools.getEAP7Context(host, port);
        Connection connection = null;
        try {
            ConnectionFactory cf = (ConnectionFactory) context.lookup(Constants.CONNECTION_FACTORY_JNDI_EAP7);
            connection = cf.createConnection();
            Queue queue = (Queue) context.lookup(queueJNDIName);
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(queue);
            connection.start();
            TextMessageBuilder mb = new TextMessageBuilder(100);

            while (Math.abs(counter) != maxMessages) {
                Message msg = mb.createMessage(new MessageCreator10(session), ArtemisJMSImplementation.getInstance());
                msg.setIntProperty("count", counter);
                sendMessage(producer, msg);
                addMessage(listOfSentMessages, msg);
            }
            connection.close();
        }catch (Exception e){
            log.error(e.toString());
            exceptions = exceptions +e.toString()+"\n";

        }finally {
            if(connection != null){
                connection.close();
            }
        }
    }

    private void sendMessage(MessageProducer producer, Message message){
        int retryCounter = 0;
        while (retryCounter < MAX_RETRIES){
            try{
                if(retryCounter >0){
                    log.info("Retry sent - number of retries: (" + retryCounter + ") message: " + message.getJMSMessageID() + ", counter: " + counter);
                }
                producer.send(message);
                log.debug("Sent message with property counter: " + counter + ", messageId:" + message.getJMSMessageID()
                        + " dupId: " + message.getStringProperty("_HQ_DUPL_ID"));
                counter++;
                break;
            }catch (Exception e){
                retryCounter++;
                log.error("Failed to send message - counter: "+ counter,e);
            }
        }
        if(retryCounter == MAX_RETRIES){
            log.error("MAX_RETRIES for producer exceeded");
        }

    }



    protected void addMessage(List<Map<String,String>> listOfSentMessages, Message message) throws JMSException {
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
        listOfSentMessages.add(mapOfPropertiesOfTheMessage);
    }



}
