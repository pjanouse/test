/*
 * JBoss, the OpenSource J2EE webOS
 * 
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.qa.hornetq.tools;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * A ControllableProxy.
 *
 * @author <a href="pslavice@jboss.com">Pavel Slavicek</a>
 * @version $Revision: 1.1 $
 */
public interface ControllableProxy extends Remote {
    /**
     * Sets block flag for communication to server
     *
     * @param isBlocked
     */
    void setBlockCommunicationToServer(boolean isBlocked) throws RemoteException;

    /**
     * Returns flag if communication to server is blocked
     *
     * @return true if communication is blocked
     */
    boolean isBlockCommunicationToServer();

    /**
     * Sets block flag for communication to client
     *
     * @param isBlocked
     */
    void setBlockCommunicationToClient(boolean isBlocked) throws RemoteException;

    /**
     * Returns flag if communication to client is blocked
     *
     * @return true if communication is blocked
     */

    boolean isBlockCommunicationToClient() throws RemoteException;

    /**
     * Stops proxy, disconnects all connection and destroys
     * ports
     */
    void stop() throws RemoteException;

    /**
     * Starts proxy, disconnects all connection and destroys
     * ports
     */
    void start() throws RemoteException;

    /**
     * Sets flag for proxy server termination
     */
    void setTerminateRequest() throws RemoteException;

    /**
     * Return flag of termination request
     *
     * @return true is there request for termination
     */
    boolean isTerminateRequest() throws RemoteException;

    /**
     * Prints debug output
     *
     * @param data
     * @param type
     */
    void debug(byte[] data, String type) throws RemoteException;
}