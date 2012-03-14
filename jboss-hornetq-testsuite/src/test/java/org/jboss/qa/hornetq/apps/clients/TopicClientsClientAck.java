package org.jboss.qa.hornetq.apps.clients;

import org.jboss.qa.hornetq.apps.Clients;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;

/**
 * This class starts publishers and subscribers on multiple topic.
 *
 * @author mnovak
 */
public class TopicClientsClientAck implements Clients {

    private static final Logger logger = Logger.getLogger(TopicClientsClientAck.class);

    private String hostnameForPublishers;
    private String hostnameForSubscribers;
    private int jndiPort;
    private String topicJndiNamePrefix;
    private int messages;
    private int numberOfTopics;
    private int numberOfPublishersPerTopic;
    private int numberOfsubscribersPerTopic;
    private List<PublisherClientAck> publishers = new ArrayList<PublisherClientAck>();
    private List<SubscriberClientAck> subscribers = new ArrayList<SubscriberClientAck>();

    public TopicClientsClientAck(int numberOfTopics, int numberOfPublishersPerTopic, int numberOfsubscribersPerTopic) {

        this("localhost", 4447, "jms/topic/testTopic", numberOfTopics, numberOfPublishersPerTopic, numberOfsubscribersPerTopic, 100);
    }

    public TopicClientsClientAck(String hostname, int jndiPort, String topicJndiNamePrefix, int numberOfTopics,
            int numberOfPublishersPerTopic, int numberOfsubscribersPerTopic, int numberOfMessages) {

        this.hostnameForSubscribers = hostname;
        this.hostnameForPublishers = hostname;
        this.jndiPort = jndiPort;
        this.topicJndiNamePrefix = topicJndiNamePrefix;
        this.numberOfTopics = numberOfTopics;
        this.numberOfPublishersPerTopic = numberOfPublishersPerTopic;
        this.numberOfsubscribersPerTopic = numberOfsubscribersPerTopic;
        this.messages = numberOfMessages;

    }

    /**
     *  Creates clients and start them.
     */
    @Override
    public void startClients() {

        List<FinalTestMessageVerifier> topicTextMessageVerifiers = null;
        
        FinalTestMessageVerifier verifier = null;

        // create publishers and subscribers
        for (int destinationNumber = 0; destinationNumber < getNumberOfTopics(); destinationNumber++) {

            SubscriberClientAck subscriber = null;
            
            topicTextMessageVerifiers = new ArrayList<FinalTestMessageVerifier>();
            
            for (int subscriberNumber = 0; subscriberNumber < getNumberOfsubscribersPerTopic(); subscriberNumber++) {
                
                subscriber = new SubscriberClientAck(getHostnameForSubscribers(), getJndiPort(), 
                        getDestionationJndiNamePrefix() + destinationNumber,
                        "subscriberClientId-" + getDestionationJndiNamePrefix() + destinationNumber + "-" + subscriberNumber,
                        "subscriberName-" + getDestionationJndiNamePrefix() + destinationNumber + "-" + subscriberNumber);

                verifier = new TextMessageVerifier();
                
                subscriber.setMessageVerifier(verifier);
                
                topicTextMessageVerifiers.add(verifier);

                getSubscribers().add(subscriber);
                
                subscriber.subscribe();
                logger.info(subscriber + "was subscribed.");
            }
            
            PublisherClientAck publisher = null;

            for (int publisherNumber = 0; publisherNumber < getNumberOfPublishersPerTopic(); publisherNumber++) {

                publisher = new PublisherClientAck(getHostnameForPublishers(), getJndiPort(),
                        getDestionationJndiNamePrefix() + destinationNumber, getMessages(), 
                        "publisherClientId-" + getDestionationJndiNamePrefix() + destinationNumber + "-" + publisherNumber);

                publisher.setMessageVerifiers(topicTextMessageVerifiers);

                getPublishers().add(publisher);

            }
        }
        
        // start subscribers
        for (Thread subscriberThread : getSubscribers()) {
            subscriberThread.start();
        }
        
        // start all clients - publishers 
        for (Thread publisherThread : getPublishers()) {
            publisherThread.start();
        }
        

    }

    /**
     * Returns false if some clients are still running. No matter how.
     *
     * @return true if all clients ended
     */
    @Override
    public boolean isFinished() throws InterruptedException {

        boolean isFinished = true;

        // check subscribers
        for (Thread subscriberThread : getSubscribers()) {

            if (subscriberThread.isAlive()) {
                isFinished = false;
            }
        }
        
        // check publishers first
        for (Thread publisherThread : getPublishers()) {

            if (publisherThread.isAlive()) {
                isFinished = false;
            }
        }
        return isFinished;

    }

    /**
     * Check whether number of sent and received messages is equal for all
     * clients and whether clients ended properly without exception.
     *
     */
    @Override
    public boolean evaluateResults() throws Exception {

        boolean isOk = true;

        logger.info("################################################################");
        logger.info("Evaluate results for topic clients with client acknowledge:");
        logger.info("hostname for publishers:" + hostnameForPublishers);
        logger.info("hostname for subscribers:" + hostnameForPublishers);
        logger.info("topicJndiPrefix:" + topicJndiNamePrefix);
        logger.info("number of topics:" + numberOfTopics);
        logger.info("number of publishers per topic:" + numberOfPublishersPerTopic);
        logger.info("number of subsribers per topic:" + numberOfsubscribersPerTopic);
        logger.info("################################################################");

        // check clients if they got an exception
        for (PublisherClientAck publisher : getPublishers()) {
            if (publisher.getException() != null) {
                isOk = false;
                logger.error("Publisher for host " + publisher.getHostname() + " and topic " + publisher.getTopicNameJndi()
                        + " got exception: " + publisher.getException().getMessage());
            }
        }

        for (SubscriberClientAck subscriber : getSubscribers()) {
            if (subscriber.getException() != null) {
                isOk = false;
                logger.error("Subscriber for host " + subscriber.getHostname() + " and topic " + subscriber.getTopicNameJndi()
                        + " got exception: " + subscriber.getException().getMessage());
            }
        }

        // check message verifiers
        for (SubscriberClientAck subscriber : getSubscribers()) {
            logger.info("################################################################");
            logger.info("Subscriber on topic: " + subscriber.getTopicNameJndi() 
                    + " with name: " + subscriber.getSubscriberName() + " -- Number of received messages: " + subscriber.getMessageVerifier().getReceivedMessages().size()
                    + " Number of sent messages: " + subscriber.getMessageVerifier().getSentMessages().size());
            if (!subscriber.getMessageVerifier().verifyMessages()) {
                isOk = false;
            }
            logger.info("################################################################");
        }

        // check exceptions
        return isOk;
    }
    
    /**
     * Stop all publishers
     */
    @Override
    public void stopClients() {
        
        for (PublisherClientAck publisher : publishers)   {
            
            publisher.stopSending();
        }
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
     * @return the hostnameForSubscribers
     */
    public String getHostnameForSubscribers() {
        return hostnameForSubscribers;
    }

    /**
     * @param hostnameForSubscribers the hostnameForSubscribers to set
     */
    public void setHostnameForSubscribers(String hostnameForSubscribers) {
        this.hostnameForSubscribers = hostnameForSubscribers;
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
    public List<PublisherClientAck> getPublishers() {
        return publishers;
    }

    /**
     * @param publishers the publishers to set
     */
    public void setPublishers(List<PublisherClientAck> publishers) {
        this.publishers = publishers;
    }

    /**
     * @return the subscribers
     */
    public List<SubscriberClientAck> getSubscribers() {
        return subscribers;
    }

    /**
     * @param subscribers the subscribers to set
     */
    public void setSubscribers(List<SubscriberClientAck> subscribers) {
        this.subscribers = subscribers;
    }

    public static void main(String[] args) throws InterruptedException, Exception {

        TopicClientsClientAck clients =
                new TopicClientsClientAck("192.168.1.1", 4447, "jms/topic/testTopic", 2, 1, 2, 300);
        clients.startClients();
        while (!clients.isFinished()) {
            Thread.sleep(1000);
        }
        
        clients.evaluateResults();

    }

}
