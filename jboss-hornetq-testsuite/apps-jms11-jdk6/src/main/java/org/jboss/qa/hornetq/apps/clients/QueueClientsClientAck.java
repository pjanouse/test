package org.jboss.qa.hornetq.apps.clients;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.HornetQTestCaseConstants;
import org.jboss.qa.hornetq.tools.ContainerUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This class starts producers and receivers on multiple queues.
 *
 * @author mnovak
 */
public class QueueClientsClientAck implements Clients {

    private static final Logger logger = Logger.getLogger(QueueClientsClientAck.class);

    private Container containerForProducers;

    private Container containerForConsumers;

    private int portForConsumers;

    private int jndiPort;

    private String queueJndiNamePrefix;

    private String queueJndiNamePrefixProducers;

    private String queueJndiNamePrefixConsumers;

    private int messages;

    private int numberOfQueues;

    private int numberOfProducersPerQueueu;

    private int numberOfConsumersPerQueueu;

    private MessageBuilder messageBuilder;

    private Container container;

    private List<ProducerClientAck> producers = new ArrayList<ProducerClientAck>();

    private List<ReceiverClientAck> receivers = new ArrayList<ReceiverClientAck>();

    private HashMap<String, FinalTestMessageVerifier> verifiers = new HashMap<String, FinalTestMessageVerifier>();



    private String containerType = HornetQTestCaseConstants.EAP6_CONTAINER;

    private int receivedMessagesAckAfter = 1000;

    public QueueClientsClientAck(Container container, int numberOfQueues, int numberOfProducersPerQueueu, int numberOfConsumersPerQueueu){
        this(container, "jms/queue/testQueue", numberOfQueues, numberOfProducersPerQueueu, numberOfConsumersPerQueueu, 100);
    }

    public QueueClientsClientAck(Container container, String queueJndiNamePrefix, int numberOfQueues,
                                 int numberOfProducersPerQueueu, int numberOfConsumersPerQueueu, int numberOfMessages) {

        this.container = container;
        this.containerType = container.getContainerType().toString();
        this.containerForConsumers = container;
        this.containerForProducers = container;
        this.jndiPort = container.getJNDIPort();
        this.portForConsumers = jndiPort;
        this.queueJndiNamePrefix = queueJndiNamePrefix;
        this.queueJndiNamePrefixProducers = queueJndiNamePrefix;
        this.queueJndiNamePrefixConsumers = queueJndiNamePrefix;
        this.numberOfQueues = numberOfQueues;
        this.numberOfProducersPerQueueu = numberOfProducersPerQueueu;
        this.numberOfConsumersPerQueueu = numberOfConsumersPerQueueu;
        this.messages = numberOfMessages;

    }

    @Deprecated
    public QueueClientsClientAck(int numberOfQueues, int numberOfProducersPerQueueu, int numberOfConsumersPerQueueu) {

        this("localhost", HornetQTestCaseConstants.PORT_JNDI_EAP6, "jms/queue/testQueue", numberOfQueues, numberOfProducersPerQueueu, numberOfConsumersPerQueueu, 100);
    }

    @Deprecated
    public QueueClientsClientAck(String hostname, int jndiPort, String queueJndiNamePrefix, int numberOfQueues,
                                 int numberOfProducersPerQueueu, int numberOfConsumersPerQueueu, int numberOfMessages) {
        this(HornetQTestCaseConstants.EAP6_CONTAINER, hostname, jndiPort, queueJndiNamePrefix, numberOfQueues, numberOfProducersPerQueueu, numberOfConsumersPerQueueu,
                    numberOfMessages);
    }

    @Deprecated
    public QueueClientsClientAck(String containerType, String hostname, int jndiPort, String queueJndiNamePrefix, int numberOfQueues,
                                 int numberOfProducersPerQueueu, int numberOfConsumersPerQueueu, int numberOfMessages) {
        this.containerType = containerType;
//        this.containerForConsumers = hostname;
//        this.containerForProducers = hostname;
        this.jndiPort = jndiPort;
        this.portForConsumers = jndiPort;
        this.queueJndiNamePrefix = queueJndiNamePrefix;
        this.queueJndiNamePrefixProducers = queueJndiNamePrefix;
        this.queueJndiNamePrefixConsumers = queueJndiNamePrefix;
        this.numberOfQueues = numberOfQueues;
        this.numberOfProducersPerQueueu = numberOfProducersPerQueueu;
        this.numberOfConsumersPerQueueu = numberOfConsumersPerQueueu;
        this.messages = numberOfMessages;
    }

    /**
     * Creates org.jboss.qa.hornetq.apps.clients and start them.
     */
    @Override
    public void startClients() {

        FinalTestMessageVerifier queueTextMessageVerifier = null;

        // create producers and receivers
        for (int destinationNumber = 0; destinationNumber < getNumberOfQueues(); destinationNumber++) {

            queueTextMessageVerifier = new TextMessageVerifier(ContainerUtils.getJMSImplementation(container));

            verifiers.put(getQueueJndiNamePrefix() + destinationNumber, queueTextMessageVerifier);

            ProducerClientAck p = null;

            for (int producerNumber = 0; producerNumber < getNumberOfProducersPerQueueu(); producerNumber++) {

                p = new ProducerClientAck(containerForProducers, queueJndiNamePrefixProducers + destinationNumber, getMessages());

                p.addMessageVerifier(queueTextMessageVerifier);

                if (messageBuilder != null) p.setMessageBuilder(messageBuilder);

                producers.add(p);

            }

            ReceiverClientAck r = null;

            for (int receiverNumber = 0; receiverNumber < getNumberOfConsumersPerQueueu(); receiverNumber++) {

                r = new ReceiverClientAck(containerForConsumers, queueJndiNamePrefixConsumers + destinationNumber);

                r.addMessageVerifier(queueTextMessageVerifier);

                r.setAckAfter(receivedMessagesAckAfter);

                receivers.add(r);
            }
        }

        // start all org.jboss.qa.hornetq.apps.clients - producers first
        for (Thread producerThread : producers) {
            producerThread.start();
        }
        // start receivers
        for (Thread receiverThread : receivers) {
            receiverThread.start();
        }

    }

    /**
     * Returns false if some org.jboss.qa.hornetq.apps.clients are still running. No matter how.
     *
     * @return true if all org.jboss.qa.hornetq.apps.clients ended
     */
    @Override
    public boolean isFinished() throws InterruptedException {

        boolean isFinished = true;

        // check producers first
        for (Thread producerThread : producers) {

            if (producerThread.isAlive()) {
                isFinished = false;
            }
        }
        // check receivers
        for (Thread receiverThread : receivers) {

            if (receiverThread.isAlive()) {
                isFinished = false;
            }
        }

        return isFinished;

    }

    /**
     * Check whether number of sent and received messages is equal for all org.jboss.qa.hornetq.apps.clients and whether org.jboss.qa.hornetq.apps.clients
     * ended properly without exception.
     */
    @Override
    public boolean evaluateResults() throws Exception {

        boolean isOk = true;

        logger.info("################################################################");
        logger.info("Evaluate results for queue org.jboss.qa.hornetq.apps.clients with client acknowledge:");
        logger.info("hostname for producers:" + containerForProducers);
        logger.info("hostname for receivers:" + containerForConsumers);
        logger.info("queueJndiPrefixForProducers:" + queueJndiNamePrefixProducers);
        logger.info("queueJndiPrefixForConsumers:" + queueJndiNamePrefixConsumers);
        logger.info("number of queues:" + numberOfQueues);
        logger.info("number of producers per queue:" + numberOfProducersPerQueueu);
        logger.info("number of receivers per queue:" + numberOfConsumersPerQueueu);
        logger.info("################################################################");

        // check org.jboss.qa.hornetq.apps.clients if they got an exception
        for (ProducerClientAck producer : producers) {
            if (producer.getException() != null) {
                isOk = false;
                logger.error("Producer for host " + producer.getHostname() + " and queue " + producer.getDestinationNameJndi() +
                        " got exception: " + producer.getException().getMessage());
            }
        }

        for (ReceiverClientAck receiver : receivers) {
            if (receiver.getException() != null) {
                isOk = false;
                logger.error("Receiver for host " + receiver.getHostname() + " and queue " + receiver.getDestinationNameJndi() +
                        " got exception: " + receiver.getException().getMessage());
            }
        }

        // check message verifiers
        for (String queue : verifiers.keySet()) {
            logger.info("################################################################");
            logger.info("Queue: " + queue + " -- Number of received messages: " + verifiers.get(queue).getReceivedMessages().size() +
                    " Number of sent messages: " + verifiers.get(queue).getSentMessages().size());
            if (!verifiers.get(queue).verifyMessages()) {
                isOk = false;
            }
            logger.info("################################################################");
        }

        // check exceptions
        return isOk;
    }

    /**
     * Stops all producers.
     */
    @Override
    public void stopClients() {
        for (ProducerClientAck producer : producers) {
            producer.stopSending();
        }
    }

    /**
     * @return the jndiPort
     */
    @Override
    public int getJndiPort() {
        return jndiPort;
    }

    /**
     * @param jndiPort the jndiPort to set
     */
    @Override
    public void setJndiPort(int jndiPort) {
        this.jndiPort = jndiPort;
    }

    /**
     * @return the queueJndiNamePrefix
     */
    public String getQueueJndiNamePrefix() {
        return queueJndiNamePrefix;
    }

    /**
     * @param queueJndiNamePrefix the queueJndiNamePrefix to set
     */
    @Override
    public void setDestinationJndiNamePrefix(String queueJndiNamePrefix) {
        this.queueJndiNamePrefix = queueJndiNamePrefix;
    }

    /**
     * @return the messages
     */
    @Override
    public int getMessages() {
        return messages;
    }

    /**
     * @param messages the messages to set
     */
    @Override
    public void setMessages(int messages) {
        this.messages = messages;
    }

    /**
     * @return the numberOfQueues
     */
    public int getNumberOfQueues() {
        return numberOfQueues;
    }

    /**
     * @param numberOfQueues the numberOfQueues to set
     */
    public void setNumberOfQueues(int numberOfQueues) {
        this.numberOfQueues = numberOfQueues;
    }

    /**
     * @return the numberOfProducersPerQueueu
     */
    public int getNumberOfProducersPerQueueu() {
        return numberOfProducersPerQueueu;
    }

    /**
     * @param numberOfProducersPerQueueu the numberOfProducersPerQueueu to set
     */
    public void setNumberOfProducersPerQueueu(int numberOfProducersPerQueueu) {
        this.numberOfProducersPerQueueu = numberOfProducersPerQueueu;
    }

    /**
     * @return the numberOfConsumersPerQueueu
     */
    public int getNumberOfConsumersPerQueueu() {
        return numberOfConsumersPerQueueu;
    }

    /**
     * @param numberOfConsumersPerQueueu the numberOfConsumersPerQueueu to set
     */
    public void setNumberOfConsumersPerQueueu(int numberOfConsumersPerQueueu) {
        this.numberOfConsumersPerQueueu = numberOfConsumersPerQueueu;
    }

    /**
     * @return the containerForProducers
     */
    public Container getContainerForProducers() {
        return containerForProducers;
    }

    /**
     * @param containerForProducers the containerForProducers to set
     */
    public void setContainerForProducers(Container containerForProducers) {
        this.containerForProducers = containerForProducers;
    }

    /**
     * @return the containerForConsumers
     */
    public Container getContainerForConsumers() {
        return containerForConsumers;
    }

    /**
     * @param containerForConsumers the containerForConsumers to set
     */
    public void setContainerForConsumers(Container containerForConsumers) {
        this.containerForConsumers = containerForConsumers;
    }

    public static void main(String[] args) throws InterruptedException, Exception {

        QueueClientsClientAck clients =
                new QueueClientsClientAck("192.168.1.1", 4447, "jms/queue/testQueue", 1, 1, 1, 1000);
        clients.startClients();
        while (!clients.isFinished()) {
            Thread.sleep(1000);
        }
        clients.evaluateResults();

    }

    /**
     * @return the queueJndiNamePrefixProducers
     */
    public String getQueueJndiNamePrefixProducers() {
        return queueJndiNamePrefixProducers;
    }

    /**
     * @param queueJndiNamePrefixProducers the queueJndiNamePrefixProducers to set
     */
    public void setQueueJndiNamePrefixProducers(String queueJndiNamePrefixProducers) {
        this.queueJndiNamePrefixProducers = queueJndiNamePrefixProducers;
    }

    /**
     * @return the queueJndiNamePrefixConsumers
     */
    public String getQueueJndiNamePrefixConsumers() {
        return queueJndiNamePrefixConsumers;
    }

    /**
     * @param queueJndiNamePrefixConsumers the queueJndiNamePrefixConsumers to set
     */
    public void setQueueJndiNamePrefixConsumers(String queueJndiNamePrefixConsumers) {
        this.queueJndiNamePrefixConsumers = queueJndiNamePrefixConsumers;
    }

    /**
     * @return the messageBuilder
     */
    public MessageBuilder getMessageBuilder() {
        return messageBuilder;
    }

    /**
     * @param messageBuilder the messageBuilder to set
     */
    public void setMessageBuilder(MessageBuilder messageBuilder) {
        this.messageBuilder = messageBuilder;
    }

    /**
     * For client_ack and session trans.
     * One consumer/subscriber will ack/commit after x messages
     */
    @Override
    public void setReceivedMessagesAckCommitAfter(int ackAfter) {
        receivedMessagesAckAfter = ackAfter;
    }

    /**
     * For client_ack and session trans.
     * Producer/Publisher will ack/commit after x messages
     */
    @Override
    public void setProducedMessagesCommitAfter(int commitAfter) {
        logger.info("No reason set ack after on client ack.");
    }

    @Override
    public List<Client> getConsumers() {
        List<Client> list = new ArrayList<Client>();
        for (Client c : receivers)  {
            list.add(c);
        }
        return list;
    }

    @Override
    public List<Client> getProducers() {
        List<Client> list = new ArrayList<Client>();
        for (Client c : producers)  {
            list.add(c);
        }
        return list;
    }

    public int getPortForConsumers() {
        return portForConsumers;
    }

    public void setPortForConsumers(int portForConsumers) {
        this.portForConsumers = portForConsumers;
    }
}
