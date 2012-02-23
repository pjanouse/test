/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.qa.hornetq.apps.clients;

import java.util.ArrayList;
import java.util.List;

/**
 *  This class starts producers and receivers on multiple queues. 
 * 
 * @author mnovak
 */
public class StartManyClientsClientAck {
    
    private String hostname;
    
    private int jndiPort;
    
    private String queueJndiNamePrefix;    
    private int messages;
    
    private int numberOfQueues;
    
    private int numberOfProducersPerQueueu;
    
    private int numberOfConsumersPerQueueu;

    private List<ProducerClientAckHA> producers = new ArrayList<ProducerClientAckHA>();
    
    private List<ReceiverClientAckHA> receivers = new ArrayList<ReceiverClientAckHA>();
   
    public StartManyClientsClientAck(int numberOfQueues, int numberOfProducersPerQueueu, int numberOfConsumersPerQueueu)  {
        
        this("localhost", 4447, "jms/queue/testQueue", numberOfQueues, numberOfProducersPerQueueu, numberOfConsumersPerQueueu, 500);
        
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
        
        // create producers and receivers
        for (int destinationNumber = 0; destinationNumber < getNumberOfQueues(); destinationNumber++)  {
            
            for (int producerNumber = 0; producerNumber < getNumberOfProducersPerQueueu(); producerNumber++) {
                
                producers.add(new ProducerClientAckHA(getHostname(), getJndiPort(), getQueueJndiNamePrefix() + destinationNumber, getMessages()));
                
            }
            
            for (int receiverNumber = 0; receiverNumber < getNumberOfConsumersPerQueueu(); receiverNumber++) {
                
                receivers.add(new ReceiverClientAckHA(getHostname(), getJndiPort(), getQueueJndiNamePrefix() + destinationNumber));
                
            }
            
        }
        
        // start all clients - producers first
        for (ProducerClientAckHA producer : producers)  {
            producer.start();
        }
        // start receivers
        for (ReceiverClientAckHA receiver : receivers)  {
            receiver.start();
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
        for (ProducerClientAckHA producer : producers)  {
            
            if (producer.isAlive()) {
                isFinished = false;
            }
        }
        // check receivers
        for (ReceiverClientAckHA receiver : receivers)  {
            
            if (receiver.isAlive()) {
                isFinished = false;
            }
        }
        
        return isFinished;
        
    }
    
    public void evaluateResults()   {
        
        throw new UnsupportedOperationException();
        
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
