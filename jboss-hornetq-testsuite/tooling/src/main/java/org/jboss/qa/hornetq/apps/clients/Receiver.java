package org.jboss.qa.hornetq.apps.clients;


import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;

import javax.jms.JMSException;
import javax.jms.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Receiver extends Client {

    private static final Logger logger = Logger.getLogger(Receiver.class);

    protected long receiveTimeout = 10000;
    protected String selector;

    protected List<Map<String, String>> listOfReceivedMessages = new ArrayList<Map<String, String>>();
    protected List<Message> listOfReceivedMessagesToBeCommited = new ArrayList<Message>();
    protected List<Message> listOfReceivedInDoubtMessages = new ArrayList<Message>();

    @Deprecated
    public Receiver(String container, String hostname, int jndiPort, String destinationNameJndi, int maxRetries) {
        super(container, hostname, jndiPort, destinationNameJndi, maxRetries);
        setTimeout(0);
    }

    public Receiver(Container container, String destinationNameJndi, long receiveTimeOut,
                    int maxRetries) {
        super(container, destinationNameJndi, maxRetries);
        this.receiveTimeout = receiveTimeOut;
        setTimeout(0);
    }

    protected void logInDoubtMessages() throws JMSException {
        String duplicatedHeader = jmsImplementation.getDuplicatedHeader();

        StringBuilder stringBuilder = new StringBuilder();
        for (Message m : listOfReceivedInDoubtMessages) {
            stringBuilder.append("messageId: ").append(m.getJMSMessageID()).append(" dupId: ").append(m.getStringProperty(duplicatedHeader) + ", \n");
        }
        logger.info("List of  in doubt messages: \n" + stringBuilder.toString());
    }

    protected void checkIfInDoubtMessagesReceivedAgainAndRemoveThemFromTheListOfInDoubts() throws JMSException {
        String duplicatedHeader = jmsImplementation.getDuplicatedHeader();

        // clone list of inDoubtMessages
        List<Message> listCloneOfInDoubtMessages = new ArrayList<Message>();
        for (Message m : listOfReceivedInDoubtMessages) {
            listCloneOfInDoubtMessages.add(m);
        }

        // if duplicate received then remove from the list of in doubt messages
        String inDoubtMessageDupId = null;
        String receivedMessageDupId = null;
        for (Message inDoubtMessage : listCloneOfInDoubtMessages) {
            inDoubtMessageDupId = inDoubtMessage.getStringProperty(duplicatedHeader);
            for (Message receivedMessage : listOfReceivedMessagesToBeCommited) {
                if (((receivedMessageDupId = receivedMessage.getStringProperty(duplicatedHeader)) != null) &&
                        receivedMessageDupId.equalsIgnoreCase(inDoubtMessageDupId)) {
                    logger.info("Duplicated in doubt message was received. Removing message with dup id: " + inDoubtMessageDupId
                            + " and messageId: " + inDoubtMessage.getJMSMessageID() + " from list of in doubt messages");
                    listOfReceivedInDoubtMessages.remove(inDoubtMessage);
                }
            }
        }
    }

    protected void logListOfAddedMessages(List<Message> listOfReceivedMessagesToBeCommited) throws JMSException {
        String duplicatedHeader = jmsImplementation.getDuplicatedHeader();

        StringBuilder stringBuilder = new StringBuilder();
        for (Message m : listOfReceivedMessagesToBeCommited) {
            stringBuilder.append("messageId: ").append(m.getJMSMessageID()).append(" dupId: ").append(m.getStringProperty(duplicatedHeader) + ", \n");
        }
        logger.info("New messages added to list of received messages: \n" + stringBuilder.toString());
    }

    public String getSelector() {
        return selector;
    }

    public void setSelector(String selector) {
        this.selector = selector;
    }

    /**
     * @return the listOfReceivedMessages
     */
    public List<Map<String, String>> getListOfReceivedMessages() {
        return listOfReceivedMessages;
    }

    /**
     * @param listOfReceivedMessages the listOfReceivedMessages to set
     */
    public void setListOfReceivedMessages(List<Map<String, String>> listOfReceivedMessages) {
        this.listOfReceivedMessages = listOfReceivedMessages;
    }

    public long getReceiveTimeout() {
        return receiveTimeout;
    }

    public void setReceiveTimeout(long receiveTimeout) {
        this.receiveTimeout = receiveTimeout;
    }
}
