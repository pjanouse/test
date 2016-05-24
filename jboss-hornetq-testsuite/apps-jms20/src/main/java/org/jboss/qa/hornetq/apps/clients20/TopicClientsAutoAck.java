package org.jboss.qa.hornetq.apps.clients20;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.tools.ContainerUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by eduda on 3.8.2015.
 */
public class TopicClientsAutoAck implements Clients {

    private static final Logger logger = Logger.getLogger(TopicClientsAutoAck.class);

    private String hostnameForPublishers;
    private String topicJndiNamePrefix;
    private int jndiPort;
    private int messages;
    private int numberOfTopics;
    private int numberOfPublishersPerTopic;
    private int numberOfsubscribersPerTopic;
    private List<PublisherAutoAck> publishers = new ArrayList<PublisherAutoAck>();
    private List<SubscriberAutoAck> subscribers = new ArrayList<SubscriberAutoAck>();
    private Container container;
    private MessageBuilder messageBuilder;

    protected JMSImplementation jmsImplementation;

    public TopicClientsAutoAck(Container container, int numberOfTopics, int numberOfPublishersPerTopic, int numberOfsubscribersPerTopic){

        this(container, "jms/topic/testTopic", numberOfTopics, numberOfPublishersPerTopic, numberOfsubscribersPerTopic, 100);
    }

    public TopicClientsAutoAck(Container container, String topicJndiNamePrefix, int numberOfTopics,
                               int numberOfPublishersPerTopic, int numberOfsubscribersPerTopic, int numberOfMessages){
        this.container = container;
        this.hostnameForPublishers = container.getHostname();
        this.topicJndiNamePrefix = topicJndiNamePrefix;
        this.jndiPort = container.getJNDIPort();
        this.numberOfTopics = numberOfTopics;
        this.numberOfPublishersPerTopic = numberOfPublishersPerTopic;
        this.numberOfsubscribersPerTopic = numberOfsubscribersPerTopic;
        this.messages = numberOfMessages;
        this.jmsImplementation = ContainerUtils.getJMSImplementation(container);
    }

    /**
     * Creates org.jboss.qa.hornetq.apps.clients and start them.
     */
    @Override
    public void startClients() {

        List<FinalTestMessageVerifier> topicTextMessageVerifiers = null;

        FinalTestMessageVerifier verifier;

        // create publishers and subscribers
        for (int destinationNumber = 0; destinationNumber < getNumberOfTopics(); destinationNumber++) {

            SubscriberAutoAck subscriber;

            topicTextMessageVerifiers = new ArrayList<FinalTestMessageVerifier>();

            for (int subscriberNumber = 0; subscriberNumber < getNumberOfsubscribersPerTopic(); subscriberNumber++) {

                subscriber = new SubscriberAutoAck(container, getDestionationJndiNamePrefix() + destinationNumber, 0, 0,
                        "subscriberClientId-" + getDestionationJndiNamePrefix() + destinationNumber + "-" + subscriberNumber,
                        "subscriberName-" + getDestionationJndiNamePrefix() + destinationNumber + "-" + subscriberNumber);

                verifier = new TextMessageVerifier(jmsImplementation);

                subscriber.addMessageVerifier(verifier);

                topicTextMessageVerifiers.add(verifier);

                getSubscribers().add(subscriber);

                subscriber.subscribe();
            }

            PublisherAutoAck publisher;

            for (int publisherNumber = 0; publisherNumber < getNumberOfPublishersPerTopic(); publisherNumber++) {

                publisher = new PublisherAutoAck(container, getDestionationJndiNamePrefix() + destinationNumber, getMessages(),
                        "publisherClientId-" + getDestionationJndiNamePrefix() + destinationNumber + "-" + publisherNumber);

                publisher.setMessageVerifiers(topicTextMessageVerifiers);

                if (messageBuilder != null) {
                    publisher.setMessageBuilder(messageBuilder);
                }

                getPublishers().add(publisher);

            }
        }

        // start subscribers
        for (Thread subscriberThread : getSubscribers()) {
            subscriberThread.start();
        }

        // start all org.jboss.qa.hornetq.apps.clients - publishers
        for (Thread publisherThread : getPublishers()) {
            publisherThread.start();
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

        // check publishers first
        for (Thread publisherThread : getPublishers()) {

            if (publisherThread.isAlive()) {
                isFinished = false;
            }
        }
        // check subscribers
        for (Thread subscriberThread : getSubscribers()) {

            if (subscriberThread.isAlive()) {
                isFinished = false;
            }
        }

        return isFinished;

    }

    @Override
    public void setJndiPort(int jndiPort) {

    }

    /**
     * Check whether number of sent and received messages is equal for all
     * org.jboss.qa.hornetq.apps.clients and whether org.jboss.qa.hornetq.apps.clients ended properly without exception.
     */
    @Override
    public boolean evaluateResults() throws Exception {

        boolean isOk = true;

        logger.info("################################################################");
        logger.info("Evaluate results for topic org.jboss.qa.hornetq.apps.clients with auto acknowledge:");
        logger.info("hostname for publishers:" + hostnameForPublishers);
        logger.info("hostname for subscribers:" + hostnameForPublishers);
        logger.info("topicJndiPrefix:" + topicJndiNamePrefix);
        logger.info("number of topics:" + numberOfTopics);
        logger.info("number of publishers per topic:" + numberOfPublishersPerTopic);
        logger.info("number of subsribers per topic:" + numberOfsubscribersPerTopic);
        logger.info("################################################################");

        // check org.jboss.qa.hornetq.apps.clients if they got an exception
        for (PublisherAutoAck publisher : getPublishers()) {
            if (publisher.getException() != null) {
                isOk = false;
                logger.error("Publisher for host " + publisher.getHostname() + " and topic " + publisher.getDestinationNameJndi()
                        + " got exception: " + publisher.getException().getMessage());
            }
        }

        for (SubscriberAutoAck subscriber : getSubscribers()) {
            if (subscriber.getException() != null) {
                isOk = false;
                logger.error("Subscriber for host " + subscriber.getHostname() + " and topic " + subscriber.getDestinationNameJndi()
                        + " got exception: " + subscriber.getException().getMessage());
            }
        }

        // check message verifiers
        for (SubscriberAutoAck subscriber : getSubscribers()) {
            if (!subscriber.verifyMessages()) {
                isOk = false;
            }
        }

        // check exceptions
        return isOk;
    }

    @Override
    public int getJndiPort() {
        return jndiPort;
    }

    /**
     * Stop all publishers
     */
    @Override
    public void stopClients() {

        for (PublisherAutoAck publisher : publishers) {

            publisher.stopSending();
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
     * @return the hostnameForPublishers
     */
    public String getHostnameForPublishers() {
        return hostnameForPublishers;
    }

    /**
     * @param hostnameForPublishers the hostnameForPublishers to set
     */
    public void setHostnameForPublishers(String hostnameForPublishers) {
        this.hostnameForPublishers = hostnameForPublishers;
    }

    /**
     * @return the topicJndiNamePrefix
     */
    public String getDestionationJndiNamePrefix() {
        return topicJndiNamePrefix;
    }

    /**
     * @param topicJndiNamePrefix the topicJndiNamePrefix to set
     */
    @Override
    public void setDestinationJndiNamePrefix(String topicJndiNamePrefix) {
        this.topicJndiNamePrefix = topicJndiNamePrefix;
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
     * @return the numberOfTopics
     */
    public int getNumberOfTopics() {
        return numberOfTopics;
    }

    /**
     * @param numberOfTopics the numberOfTopics to set
     */
    public void setNumberOfTopics(int numberOfTopics) {
        this.numberOfTopics = numberOfTopics;
    }

    /**
     * @return the numberOfPublishersPerTopic
     */
    public int getNumberOfPublishersPerTopic() {
        return numberOfPublishersPerTopic;
    }

    /**
     * @param numberOfPublishersPerTopic the numberOfPublishersPerTopic to set
     */
    public void setNumberOfPublishersPerTopic(int numberOfPublishersPerTopic) {
        this.numberOfPublishersPerTopic = numberOfPublishersPerTopic;
    }

    /**
     * @return the numberOfsubscribersPerTopic
     */
    public int getNumberOfsubscribersPerTopic() {
        return numberOfsubscribersPerTopic;
    }

    /**
     * @param numberOfsubscribersPerTopic the numberOfsubscribersPerTopic to set
     */
    public void setNumberOfsubscribersPerTopic(int numberOfsubscribersPerTopic) {
        this.numberOfsubscribersPerTopic = numberOfsubscribersPerTopic;
    }

    /**
     * @return the publishers
     */
    public List<PublisherAutoAck> getPublishers() {
        return publishers;
    }

    /**
     * @param publishers the publishers to set
     */
    public void setPublishers(List<PublisherAutoAck> publishers) {
        this.publishers = publishers;
    }

    /**
     * @return the subscribers
     */
    public List<SubscriberAutoAck> getSubscribers() {
        return subscribers;
    }

    /**
     * @param subscribers the subscribers to set
     */
    public void setSubscribers(List<SubscriberAutoAck> subscribers) {
        this.subscribers = subscribers;
    }

    /**
     * For client_ack and session trans.
     * One consumer/subscriber will ack/commit after x messages
     */
    @Override
    public void setReceivedMessagesAckCommitAfter(int ackAfter) {
        logger.info("This values can't be set for Auto acknowledge.");
    }

    /**
     * For client_ack and session trans.
     * Producer/Publisher will ack/commit after x messages
     */
    @Override
    public void setProducedMessagesCommitAfter(int commitAfter) {
        logger.info("This values can't be set for Auto acknowledge.");
    }

    @Override
    public List<Client> getConsumers() {
        List<Client> list = new ArrayList<Client>();
        for (Client c : subscribers)  {
            list.add(c);
        }
        return list;
    }

    @Override
    public List<Client> getProducers() {
        List<Client> list = new ArrayList<Client>();
        for (Client c : publishers)  {
            list.add(c);
        }
        return list;
    }
}
