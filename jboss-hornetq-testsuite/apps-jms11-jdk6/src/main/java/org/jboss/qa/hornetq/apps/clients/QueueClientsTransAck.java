package org.jboss.qa.hornetq.apps.clients;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.HornetQTestCaseConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This class starts producers and receivers on multiple queues.
 *
 * @author mnovak
 */
public class QueueClientsTransAck implements Clients {

    private static final Logger logger = Logger.getLogger(QueueClientsTransAck.class);

    private String hostnameForProducers;

    private String hostnameForConsumers;

    private int jndiPort;

    private String queueJndiNamePrefix;

    private int messages;

    private int numberOfQueues;

    private int numberOfProducersPerQueueu;

    private int numberOfConsumersPerQueueu;

    private List<ProducerTransAck> producers = new ArrayList<ProducerTransAck>();

    private List<ReceiverTransAck> receivers = new ArrayList<ReceiverTransAck>();

    private HashMap<String, FinalTestMessageVerifier> verifiers = new HashMap<String, FinalTestMessageVerifier>();

    private String container = HornetQTestCaseConstants.EAP6_CONTAINER;

    private MessageBuilder messageBuilder;

    private int receivedMessagesAckAfter = 1000;
    private int producedMessagesAckAfter = 1000;

    public QueueClientsTransAck(int numberOfQueues, int numberOfProducersPerQueueu, int numberOfConsumersPerQueueu) {

        this("localhost", HornetQTestCaseConstants.PORT_JNDI_EAP6, "jms/queue/testQueue", numberOfQueues, numberOfProducersPerQueueu, numberOfConsumersPerQueueu, 1000);
    }

    public QueueClientsTransAck(String hostname, int jndiPort, String queueJndiNamePrefix, int numberOfQueues,
                                int numberOfProducersPerQueueu, int numberOfConsumersPerQueueu, int numberOfMessages) {
        this(HornetQTestCaseConstants.EAP6_CONTAINER, hostname, jndiPort, queueJndiNamePrefix, numberOfQueues,
                numberOfProducersPerQueueu, numberOfConsumersPerQueueu, numberOfMessages);
    }

    public QueueClientsTransAck(String container, String hostname, int jndiPort, String queueJndiNamePrefix, int numberOfQueues,
                                int numberOfProducersPerQueueu, int numberOfConsumersPerQueueu, int numberOfMessages) {
        this.container = container;
        this.hostnameForConsumers = hostname;
        this.hostnameForProducers = hostname;
        this.jndiPort = jndiPort;
        this.queueJndiNamePrefix = queueJndiNamePrefix;
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

            queueTextMessageVerifier = new TextMessageVerifier();

            verifiers.put(getQueueJndiNamePrefix() + destinationNumber, queueTextMessageVerifier);

            ProducerTransAck p;

            for (int producerNumber = 0; producerNumber < getNumberOfProducersPerQueueu(); producerNumber++) {

                p = new ProducerTransAck(container, getHostnameForProducers(), getJndiPort(), getQueueJndiNamePrefix() + destinationNumber, getMessages());

                p.setMessageVerifier(queueTextMessageVerifier);

                if (messageBuilder != null) {
                    p.setMessageBuilder(messageBuilder);
                }

                p.setCommitAfter(producedMessagesAckAfter);

                producers.add(p);

            }

            ReceiverTransAck r;

            for (int receiverNumber = 0; receiverNumber < getNumberOfConsumersPerQueueu(); receiverNumber++) {

                r = new ReceiverTransAck(container, getHostnameForConsumers(), getJndiPort(), getQueueJndiNamePrefix() + destinationNumber);

                r.setMessageVerifier(queueTextMessageVerifier);

                r.setCommitAfter(receivedMessagesAckAfter);

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
                logger.info("Producer: " + producerThread + " is still alive.");
            } else {
                logger.info("Producer: " + producerThread + " is finished.");
            }

        }
        // check receivers
        for (Thread receiverThread : receivers) {

            if (receiverThread.isAlive()) {
                isFinished = false;
                logger.info("Producer: " + receiverThread + " is still alive.");
            } else {
                logger.info("Producer: " + receiverThread + " is finished.");
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
        logger.info("Evaluate results for queue org.jboss.qa.hornetq.apps.clients with transaction acknowledge:");
        logger.info("hostname for producers:" + hostnameForProducers);
        logger.info("hostname for receivers:" + hostnameForConsumers);
        logger.info("queueJndiPrefix:" + queueJndiNamePrefix);
        logger.info("number of queues:" + numberOfQueues);
        logger.info("number of producers per queue:" + numberOfProducersPerQueueu);
        logger.info("number of receivers per queue:" + numberOfConsumersPerQueueu);
        logger.info("################################################################");

        // check org.jboss.qa.hornetq.apps.clients if they got an exception
        for (ProducerTransAck producer : producers) {
            if (producer.getException() != null) {
                isOk = false;
                logger.error("Producer for host " + producer.getHostname() + " and queue " + producer.getQueueNameJndi() +
                        " got exception: " + producer.getException().getMessage());
            }
        }

        for (ReceiverTransAck receiver : receivers) {
            if (receiver.getException() != null) {
                isOk = false;
                logger.error("Receiver for host " + receiver.getHostname() + " and queue " + receiver.getQueueNameJndi() +
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
        for (ProducerTransAck producer : producers) {
            producer.stopSending();
        }
    }

    /**
     * Sets message builder for producers/publishers
     *
     * @param messageBuilder message builder
     */
    @Override
    public void setMessageBuilder(MessageBuilder messageBuilder) {
        this.messageBuilder = messageBuilder;
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
     * @return the hostnameForProducers
     */
    public String getHostnameForProducers() {
        return hostnameForProducers;
    }

    /**
     * @param hostnameForProducers the hostnameForProducers to set
     */
    public void setHostnameForProducers(String hostnameForProducers) {
        this.hostnameForProducers = hostnameForProducers;
    }

    /**
     * @return the hostnameForConsumers
     */
    public String getHostnameForConsumers() {
        return hostnameForConsumers;
    }

    /**
     * @param hostnameForConsumers the hostnameForConsumers to set
     */
    public void setHostnameForConsumers(String hostnameForConsumers) {
        this.hostnameForConsumers = hostnameForConsumers;
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
        producedMessagesAckAfter = commitAfter;
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

    public static void main(String[] args) throws Exception {

        QueueClientsTransAck clients =
                new QueueClientsTransAck("192.168.1.1", 4447, "jms/queue/testQueue", 1, 1, 1, 1000);
        clients.startClients();

        while (!clients.isFinished()) {
            Thread.sleep(1000);
        }

        clients.evaluateResults();

    }

}
