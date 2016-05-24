package org.jboss.qa.hornetq.apps.clients;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCaseConstants;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.impl.ArtemisJMSImplementation;
import org.jboss.qa.hornetq.apps.impl.HornetqJMSImplementation;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.ContainerUtils;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.naming.Context;
import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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

    protected String hostname;
    protected int port;
    protected String destinationNameJndi;
    protected long timeout = 100;
    protected int counter = 0;
    protected int maxRetries = 10;
    protected Exception exception = null;

    protected boolean isSecurityEnabled = false;
    protected String userName;
    protected String password;

    protected AtomicBoolean running = new AtomicBoolean(true);

    protected Container container;
    protected JMSImplementation jmsImplementation;
    protected List<FinalTestMessageVerifier> messageVerifiers = new ArrayList<FinalTestMessageVerifier>();

    /**
     * Creates client for the given container.
     *
     * @param currentContainerForTest currentContainerForTest - see @HornetQTestCaseConstants
     */
    @Deprecated
    public Client(String currentContainerForTest, String hostname, int jndiPort, String destinationNameJndi, long timeout, int maxRetries) {

        if (EAP5_CONTAINER.equals(currentContainerForTest)) {
            currentContainer = EAP5_CONTAINER;
            jmsImplementation = HornetqJMSImplementation.getInstance();
        } else if (EAP5_WITH_JBM_CONTAINER.equals(currentContainerForTest)) {
            currentContainer =  EAP5_WITH_JBM_CONTAINER;
            jmsImplementation = HornetqJMSImplementation.getInstance();
        } else if (EAP6_LEGACY_CONTAINER.equals(currentContainerForTest)) {
            currentContainer = EAP6_LEGACY_CONTAINER;
            jmsImplementation = HornetqJMSImplementation.getInstance();
        }  else if (EAP6_CONTAINER.equals(currentContainerForTest)) {
            currentContainer = EAP6_CONTAINER;
            jmsImplementation = HornetqJMSImplementation.getInstance();
        }  else {
            currentContainer = EAP7_CONTAINER;
            jmsImplementation = ArtemisJMSImplementation.getInstance();
        }
        this.hostname = hostname;
        this.port = jndiPort;
        this.destinationNameJndi = destinationNameJndi;
        this.timeout = timeout;
        this.maxRetries = maxRetries;
    }

    public Client(Container container, String destinationNameJndi, long timeout,
                  int maxRetries) {
        this.container = container;
        this.currentContainer = container.getContainerType().toString();
        this.jmsImplementation = ContainerUtils.getJMSImplementation(container);
        this.hostname = container.getHostname();
        this.port = container.getJNDIPort();
        this.destinationNameJndi = destinationNameJndi;
        this.timeout = timeout;
        this.maxRetries = maxRetries;
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

        if (currentContainer.equals(EAP6_CONTAINER) || currentContainer.equals(EAP6_DOMAIN_CONTAINER)) {
            logger.info("Create EAP 6 InitialContext.");
            context = JMSTools.getEAP6Context(hostname, port, Constants.JNDI_CONTEXT_TYPE.NORMAL_CONTEXT);
        } else {
            logger.info("Create EAP 7 InitialContext to hostname: " + hostname + " and port: " + port);
            context = JMSTools.getEAP7Context(hostname, port, Constants.JNDI_CONTEXT_TYPE.NORMAL_CONTEXT);
        }

        return context;
    }

    @Deprecated
    protected String getConnectionFactoryJndiName() {
        if (currentContainer.equals(EAP5_CONTAINER) || currentContainer.equals(EAP5_WITH_JBM_CONTAINER)) {
            return CONNECTION_FACTORY_JNDI_EAP5;
        } if (currentContainer.equals(EAP6_LEGACY_CONTAINER)) {
            return CONNECTION_FACTORY_JNDI_EAP6_FULL_NAME;
        } else {
            return CONNECTION_FACTORY_JNDI_EAP6;
        }
    }

    protected Message cleanMessage(Message m) throws JMSException {

        String dupId = m.getStringProperty(jmsImplementation.getDuplicatedHeader());
        String inMessageId = m.getStringProperty("inMessageId");
        String JMSXGroupID = m.getStringProperty("JMSXGroupID");
        m.clearBody();
        m.clearProperties();
        m.setStringProperty(jmsImplementation.getDuplicatedHeader(), dupId);
        m.setStringProperty("inMessageId", inMessageId);
        if (JMSXGroupID != null) {
            m.setStringProperty("JMSXGroupID", JMSXGroupID);
        }
        return m;
    }

    protected void addMessage(List<Map<String,String>> listOfReceivedMessages, Message message) throws JMSException {
        Map<String, String> mapOfPropertiesOfTheMessage = new HashMap<String,String>();
        mapOfPropertiesOfTheMessage.put("messageId", message.getJMSMessageID());
        if (message.getStringProperty(jmsImplementation.getDuplicatedHeader()) != null)   {
            mapOfPropertiesOfTheMessage.put(jmsImplementation.getDuplicatedHeader(), message.getStringProperty(jmsImplementation.getDuplicatedHeader()));
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

    protected void addSetOfMessages(List<Map<String,String>> listOfReceivedMessages, Set<Message> messages) throws JMSException {
        for (Message m : messages)  {
            addMessage(listOfReceivedMessages, m);
        }
    }

    public void forcedStop() {
        running.set(false);
        this.interrupt();
    }

    public int getCount() {
        return counter;
    }

    public void incrementCount() {
        counter++;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    protected static int getJNDIPort() {
        return 4447;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDestinationNameJndi() {
        return destinationNameJndi;
    }

    public void setDestinationNameJndi(String destinationNameJndi) {
        this.destinationNameJndi = destinationNameJndi;
    }

    public int getCounter() {
        return counter;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public List<FinalTestMessageVerifier> getMessageVerifiers() {
        return messageVerifiers;
    }

    public void setMessageVerifiers(List<FinalTestMessageVerifier> messageVerifiers) {
        this.messageVerifiers = messageVerifiers;
    }

    public void addMessageVerifier(FinalTestMessageVerifier messageVerifier) {
        this.messageVerifiers.add(messageVerifier);
    }

    public boolean verifyMessages() throws JMSException {
        if (messageVerifiers == null) {
            return true;
        }

        boolean result = true;
        for (FinalTestMessageVerifier verifier : messageVerifiers) {
            result = result && verifier.verifyMessages();
        }
        return result;
    }

    public void addSendMessages(List<Map<String, String>> messages) {
        if (messageVerifiers != null) {
            for (FinalTestMessageVerifier verifier : messageVerifiers) {
                verifier.addSendMessages(messages);
            }
        }
    }

    public void addReceivedMessages(List<Map<String, String>> messages) {
        if (messageVerifiers != null) {
            for (FinalTestMessageVerifier verifier : messageVerifiers) {
                verifier.addReceivedMessages(messages);
            }
        }
    }

    public boolean isSecurityEnabled() {
        return isSecurityEnabled;
    }

    public void setSecurityEnabled(boolean securityEnabled) {
        isSecurityEnabled = securityEnabled;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * @return the exception
     */
    public Exception getException() {
        return exception;
    }

    /**
     * @param exception the exception to set
     */
    public void setException(Exception exception) {
        this.exception = exception;
    }
}
