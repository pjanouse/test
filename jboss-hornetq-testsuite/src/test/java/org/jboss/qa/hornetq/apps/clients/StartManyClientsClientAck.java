/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.qa.hornetq.apps.clients;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.MessageVerifier;
import org.jboss.qa.hornetq.apps.impl.QueueTextMessageVerifier;

/**
 *  This class starts producers and receivers on multiple queues. 
 * 
 * @author mnovak
 */
public class StartManyClientsClientAck {
    
    private static final Logger logger = Logger.getLogger(StartManyClientsClientAck.class);

    private String hostname;
    
    private int jndiPort;
    
    private String queueJndiNamePrefix;    

    private int messages;
    
    private int numberOfQueues;
    
    private int numberOfProducersPerQueueu;
    
    private int numberOfConsumersPerQueueu;

    private List<ProducerClientAckHA> producers = new ArrayList<ProducerClientAckHA>();
    
    private List<ReceiverClientAckHA> receivers = new ArrayList<ReceiverClientAckHA>();
    
    private HashMap<String,MessageVerifier> verifiers = new HashMap<String,MessageVerifier>();
   
    public StartManyClientsClientAck(int numberOfQueues, int numberOfProducersPerQueueu, int numberOfConsumersPerQueueu)  {
        
        this("localhost", 4447, "jms/queue/testQueue", numberOfQueues, numberOfProducersPerQueueu, numberOfConsumersPerQueueu, 100);
    }
    
    public StartManyClientsClientAck(String hostname, int jndiPort, String queueJndiNamePrefix, int numberOfQueues,
             int numberOfProducersPerQueueu, int numberOfConsumersPerQueueu, int numberOfMessages)  {
        
        this.hostname = hostname;
        this.jndiPort = jndiPort;
        this.queueJndiNamePrefix = queueJndiNamePrefix;
        this.numberOfQueues = numberOfQueues;
        this.numberOfProducersPerQueueu = numberOfProducersPerQueueu;
        this.numberOfConsumersPerQueueu = numberOfConsumersPerQueueu;
        this.messages = numberOfMessages;
        
    }
    
    /**
     * Creates clients and start them.
     */
    public void startClients() {
        
        MessageVerifier queueTextMessageVerifier = null;
        
        // create producers and receivers
        for (int destinationNumber = 0; destinationNumber < getNumberOfQueues(); destinationNumber++)  {
            
            queueTextMessageVerifier = new QueueTextMessageVerifier();
            
            verifiers.put(getQueueJndiNamePrefix() + destinationNumber, queueTextMessageVerifier);
            
            ProducerClientAckHA p = null;
            
            for (int producerNumber = 0; producerNumber < getNumberOfProducersPerQueueu(); producerNumber++) {
                
                p = new ProducerClientAckHA(getHostname(), getJndiPort(), getQueueJndiNamePrefix() + destinationNumber, getMessages());
                
                p.setMessageVerifier(queueTextMessageVerifier);
                
                producers.add(p);
                
            }
            
            ReceiverClientAckHA r = null;
            
            for (int receiverNumber = 0; receiverNumber < getNumberOfConsumersPerQueueu(); receiverNumber++) {
                
                r = new ReceiverClientAckHA(getHostname(), getJndiPort(), getQueueJndiNamePrefix() + destinationNumber);
                
                r.setMessageVerifier(queueTextMessageVerifier);
                
                receivers.add(r);
            }
            
        }
        
        // start all clients - producers first
        for (Thread producerThread : producers)  {
            producerThread.start();
        }
        // start receivers
        for (Thread receiverThread : receivers)  {
            receiverThread.start();
        }
        
    }
    
    /** 
     * Returns false if some clients are still running. No matter how.
     *      
     * @return true if all clients ended
     */
    public boolean isFinished() throws InterruptedException   {
        
        boolean isFinished = true;
        
        // check producers first
        for (Thread producerThread : producers)  {
            
            if (producerThread.isAlive()) {
                isFinished = false;
            }
        }
        // check receivers
        for (Thread receiverThread : receivers)  {
            
            if (receiverThread.isAlive()) {
                isFinished = false;
            }
        }
        
        return isFinished;
        
    }
    
    /**
     * Check whether number of sent and received messages is equal for all clients and whether clients
     * ended properly without exception.
     * 
     */
    public boolean evaluateResults() throws Exception   {
        
        boolean isOk = true;
        
        // check clients if they got an exception
        for (ProducerClientAckHA producer : producers)  {
            if (producer.getException() != null)    {
                isOk = false;
                logger.error("Receiver for host " + producer.getHostname() + " and queue " + producer.getQueueNameJndi() + 
                        " got exception: " + producer.getException().getMessage());
            }
        }
        // start receivers
        for (ReceiverClientAckHA receiver : receivers)  {
            if (receiver.getException() != null)    {
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
            if (!verifiers.get(queue).verifyMessages())  {
                isOk = false;
            }
            logger.info("################################################################");
        }
        return isOk;
        
    }
    
    /**
     * @return the hostname
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * @param hostname the hostname to set
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    /**
     * @return the jndiPort
     */
    public int getJndiPort() {
        return jndiPort;
    }

    /**
     * @param jndiPort the jndiPort to set
     */
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
    public void setQueueJndiNamePrefix(String queueJndiNamePrefix) {
        this.queueJndiNamePrefix = queueJndiNamePrefix;
    }

    /**
     * @return the messages
     */
    public int getMessages() {
        return messages;
    }

    /**
     * @param messages the messages to set
     */
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

   
    
}
