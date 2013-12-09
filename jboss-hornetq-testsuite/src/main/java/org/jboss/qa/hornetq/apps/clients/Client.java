package org.jboss.qa.hornetq.apps.clients;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.HornetQTestCaseConstants;
import org.jboss.qa.hornetq.JMSTools;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.naming.Context;
import javax.naming.NamingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * Parent client class.
 *
 * Creates abstract layer for creating Context for EAP 5 and EAP 6 server.
 *
 * @author  mnovak@redhat.com
 *
 */
public class Client extends Thread implements HornetQTestCaseConstants {

    private static final Logger logger = Logger.getLogger(Client.class);
    private String currentContainer = EAP6_CONTAINER;
    private String connectionFactoryJndiName = CONNECTION_FACTORY_JNDI_EAP6;
    private int timeout = 100;
    protected int counter = 0;

    /**
     * Creates client for the given container.
     *
     * @param currentContainerForTest currentContainerForTest - can be "EAP 5, EAP 6"
     */
    public Client(String currentContainerForTest) {

        if (EAP5_CONTAINER.equals(currentContainerForTest)) {
            currentContainer = EAP5_CONTAINER;
        } else if (EAP5_WITH_JBM_CONTAINER.equals(currentContainerForTest)) {
            currentContainer =  EAP5_WITH_JBM_CONTAINER;
        }  else {
            currentContainer = EAP6_CONTAINER;
        }
    }

    /**
     *  Returns jndi context.
     *
     * @param hostname hostname
     * @param port port
     * @return Context
     * @throws NamingException
     */
    protected Context getContext(String hostname, int port) throws NamingException {

        Context context;

        if (currentContainer.equals(EAP5_CONTAINER) || currentContainer.equals(EAP5_WITH_JBM_CONTAINER)) {
            context = JMSTools.getEAP5Context(hostname, port);
        } else {
            context = JMSTools.getEAP6Context(hostname, port);
        }

        return context;
    }

    protected String getConnectionFactoryJndiName() {
        if (currentContainer.equals(EAP5_CONTAINER) || currentContainer.equals(EAP5_WITH_JBM_CONTAINER)) {
            return CONNECTION_FACTORY_JNDI_EAP5;
        } else {
            return CONNECTION_FACTORY_JNDI_EAP6;
        }
    }

    protected Message cleanMessage(Message m) throws JMSException {

        String dupId = m.getStringProperty("_HQ_DUPL_ID");
        String inMessageId = m.getStringProperty("inMessageId");
        m.clearBody();
        m.clearProperties();
        m.setStringProperty("_HQ_DUPL_ID", dupId);
        m.setStringProperty("inMessageId", inMessageId);
        return m;
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
        listOfReceivedMessages.add(mapOfPropertiesOfTheMessage);
    }

    protected void addMessages(List<Map<String,String>> listOfReceivedMessages, List<Message> messages) throws JMSException {
        for (Message m : messages)  {
            addMessage(listOfReceivedMessages, m);
        }
    }

    protected void addSetOfMessages(List<Map<String,String>> listOfReceivedMessages, Set<Message> messages) throws JMSException {
        for (Message m : messages)  {
            addMessage(listOfReceivedMessages, m);
        }
    }

    public int getCount() {
        return counter;
    }

    public void incrementCount() {
        counter++;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

}
