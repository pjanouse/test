package org.jboss.qa.artemis.test.clients.clients;


/**
 * Interface for operations capable of closing connected org.jboss.qa.hornetq.apps.clients through some server-side means.
 */
public interface ClientCloser {

    boolean closeClients() throws Exception;

}
