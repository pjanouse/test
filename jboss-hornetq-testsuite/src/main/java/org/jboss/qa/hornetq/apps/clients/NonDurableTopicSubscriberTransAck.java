package org.jboss.qa.hornetq.apps.clients;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TransactionRolledBackException;
import org.apache.log4j.Logger;


public class NonDurableTopicSubscriberTransAck extends NonDurableTopicSubscriber {

    private static final Logger logger = Logger.getLogger(NonDurableTopicSubscriberTransAck.class);

    private final int commitAfter;

    private List<Message> listOfReceivedMessagesToBeCommited = new ArrayList<Message>();
    private Set<Message> setOfReceivedMessagesWithPossibleDuplicatesForLaterDuplicateDetection = new HashSet<Message>();


    public NonDurableTopicSubscriberTransAck(String hostname, int port, String topicJndiName) {
        super(EAP6_CONTAINER, hostname, port, topicJndiName, TimeUnit.SECONDS.toMillis(30), 5);
        this.commitAfter = 10;
    }

    public NonDurableTopicSubscriberTransAck(String containerType, String hostname, int port,
            String topicJndiName) {

        super(containerType, hostname, port, topicJndiName, TimeUnit.SECONDS.toMillis(30), 5);
        this.commitAfter = 10;
    }

    public NonDurableTopicSubscriberTransAck(String hostname, int port, String topicJndiName,
            long receiveTimeout, int commitAfter, int maxRetries) {

        super(EAP6_CONTAINER, hostname, port, topicJndiName, receiveTimeout, maxRetries);
        this.commitAfter = commitAfter;
    }

    public NonDurableTopicSubscriberTransAck(String containerType, String hostname, int port,
            String topicJndiName, long receiveTimeout, int commitAfter, int maxRetries) {

        super(containerType, hostname, port, topicJndiName, receiveTimeout, maxRetries);
        this.commitAfter = commitAfter;
    }

    @Override
    protected Session createSession(Connection connection) throws JMSException {
        return connection.createSession(true, Session.SESSION_TRANSACTED);
    }

    @Override
    protected void postReceive(Message receivedMsg) throws Exception {
        if (counter % commitAfter == 0) {
            commitSession();
        }
    }

    public void commitSession() throws Exception {
        int numberOfRetries = 0;

        while (numberOfRetries < getMaxRetries()) {
            try {
                areThereDuplicatesInLaterDetection();
                session.commit();
                logger.info("Receiver for node: " + getHostname() + ". Received message - count: "
                        + getCount() + " SENT COMMIT");

                addMessages(listOfReceivedMessages, listOfReceivedMessagesToBeCommited);
                StringBuilder stringBuilder = new StringBuilder();
                for (Message m : listOfReceivedMessagesToBeCommited) {
                    stringBuilder.append("messageId: ").append(m.getJMSMessageID()).append(" dupId: ")
                            .append(m.getStringProperty("_HQ_DUPL_ID" + "\n"));
                }
                logger.debug("Adding messages: " + stringBuilder.toString());

                return;

            } catch (TransactionRolledBackException ex) {
                logger.error(" Receiver - COMMIT FAILED - TransactionRolledBackException thrown during commit: "
                        + ex.getMessage() + ". Receiver for node: " + getHostname()
                        + ". Received message - count: " + counter + ", retrying receive", ex);
                // all unacknowledge messges will be received again
                ex.printStackTrace();
                counter = counter - listOfReceivedMessagesToBeCommited.size();
                //                setOfReceivedMessagesWithPossibleDuplicates.clear();

                return;

            } catch (JMSException ex) {
                // we need to know which messages we got in the first try because we need to detect possible duplicates
                //                setOfReceivedMessagesWithPossibleDuplicates.addAll
                // (listOfReceivedMessagesToBeCommited);
                setOfReceivedMessagesWithPossibleDuplicatesForLaterDuplicateDetection
                        .addAll(listOfReceivedMessagesToBeCommited);

                addMessages(listOfReceivedMessages, listOfReceivedMessagesToBeCommited);
                StringBuilder stringBuilder = new StringBuilder();
                for (Message m : listOfReceivedMessagesToBeCommited) {
                    stringBuilder.append("messageId: ").append(m.getJMSMessageID()).append(" dupId: ")
                            .append(m.getStringProperty("_HQ_DUPL_ID" + "\n"));
                }
                logger.debug("Adding messages: " + stringBuilder.toString());

                logger.error(" Receiver - JMSException thrown during commit: " + ex.getMessage()
                                + ". Receiver for node: " + getHostname()
                                + ". Received message - count: " + counter
                                + ", COMMIT will be tried again - TRY:" + numberOfRetries, ex);
                ex.printStackTrace();
                numberOfRetries++;
            } finally {
                // we clear this list because next time we get new or duplicated messages and we compare it with set
                // possible duplicates
                listOfReceivedMessagesToBeCommited.clear();
            }
        }

        throw new Exception("FAILURE - MaxRetry reached for receiver for node: " + getHostname()
                + " during acknowledge");
    }

    private boolean areThereDuplicatesInLaterDetection() throws JMSException {
        boolean isDup = false;

        Set<String> setOfReceivedMessages = new HashSet<String>();
        for (Message m : listOfReceivedMessagesToBeCommited) {
            setOfReceivedMessages.add(m.getStringProperty("_HQ_DUPL_ID"));
        }
        StringBuilder foundDuplicates = new StringBuilder();
        for (Message m : setOfReceivedMessagesWithPossibleDuplicatesForLaterDuplicateDetection) {
            if (!setOfReceivedMessages.add(m.getStringProperty("_HQ_DUPL_ID"))) {
                foundDuplicates.append(m.getJMSMessageID());
                counter -= 1;
                // remove this duplicate from the list
                List<Message> iterationList = new ArrayList<Message>(listOfReceivedMessagesToBeCommited);
                for (Message receivedMessage : iterationList) {
                    if (receivedMessage.getStringProperty("_HQ_DUPL_ID").equals(m.getStringProperty("_HQ_DUPL_ID"))) {
                        listOfReceivedMessagesToBeCommited.remove(receivedMessage);
                    }
                }

                isDup = true;
            }
        }
        if (!"".equals(foundDuplicates.toString())) {
            logger.info("Later detection found duplicates, will be discarded: " + foundDuplicates.toString());
            logger.info("List of messages to be added to list: " + listOfReceivedMessagesToBeCommited.toString());
        }
        return isDup;
    }

}
