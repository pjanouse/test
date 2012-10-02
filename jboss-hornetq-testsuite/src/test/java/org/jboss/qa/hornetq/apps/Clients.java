/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.qa.hornetq.apps;

/**
 * @author mnovak
 */
public interface Clients {

    /**
     * Check whether number of sent and received messages is equal for all clients and whether clients
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
     * Returns false if some clients are still running. No matter how.
     *
     * @return true if all clients ended
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
     * Creates clients and start them.
     */
    void startClients();

    /**
     * Stops all producers which results in stop of all clients.
     */
    public void stopClients();

    /**
     * Sets message builder for producers/publishers
     *
     * @param messageBuilder message builder
     */
    public void setMessageBuilder(MessageBuilder messageBuilder);

}
