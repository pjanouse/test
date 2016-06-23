package org.jboss.qa.hornetq.apps.ejb;

/**
 * Created by mnovak on 1/21/14.
 */
public interface SimpleSendEJB {

    void createConnection();

    void closeConnection();

    /**
     * Sends message to queue.
     *
     */
    void sendMessage();

    int sendCount();
}

