/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.qa.hornetq.apps;

import org.jboss.qa.hornetq.apps.clients.*;

import java.util.List;

/**
 * @author mnovak
 */
public interface Clients {

    /**
     * Check whether number of sent and received messages is equal for all org.jboss.qa.hornetq.apps.clients and whether org.jboss.qa.hornetq.apps.clients
     * ended properly without exception.
     */
    boolean evaluateResults() throws Exception;

    /**
     * @return the jndiPort
     */
    int getJndiPort();

    /**
     * @return the messages
     */
    int getMessages();

    /**
     * Returns false if some org.jboss.qa.hornetq.apps.clients are still running. No matter how.
     *
     * @return true if all org.jboss.qa.hornetq.apps.clients ended
     */
    boolean isFinished() throws InterruptedException;

    /**
     * @param jndiPort the jndiPort to set
     */
    void setJndiPort(int jndiPort);

    /**
     * @param messages the messages to set
     */
    void setMessages(int messages);

    /**
     * @param queueJndiNamePrefix the queueJndiNamePrefix to set
     */
    void setDestinationJndiNamePrefix(String queueJndiNamePrefix);

    /**
     * Creates org.jboss.qa.hornetq.apps.clients and start them.
     */
    void startClients();

    /**
     * Stops all producers which results in stop of all org.jboss.qa.hornetq.apps.clients.
     */
    public void stopClients();

    /**
     * Sets message builder for producers/publishers
     *
     * @param messageBuilder message builder
     */
    public void setMessageBuilder(MessageBuilder messageBuilder);

    /**
     * For client_ack and session trans.
     * One consumer/subscriber will ack/commit after x messages
     */
    public void setReceivedMessagesAckCommitAfter(int ackAfter);

    /**
     * For client_ack and session trans.
     * One consumer/subscriber will ack/commit after x messages
     */
    public void setProducedMessagesCommitAfter(int commitAfter);

    public List<Client> getConsumers();
    public List<Client> getProducers();
}
