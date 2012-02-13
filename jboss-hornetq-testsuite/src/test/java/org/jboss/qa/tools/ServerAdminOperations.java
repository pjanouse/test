/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.qa.tools;


import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.jboss.as.arquillian.container.Authentication.getCallbackHandler;

/**
 *
 * @author mnovak
 */
public class ServerAdminOperations {
    
    private final ModelControllerClient modelControllerClient;
    
    /**
     * Default constructor
     */
    public ServerAdminOperations() {
        this("localhost", 9999);
    }

    /**
     * Constructor
     *
     * @param hostName host with the administration
     * @param port port where is administration available
     */
    public ServerAdminOperations(final String hostName, final int port) {
        try {
            InetAddress inetAddress = InetAddress.getByName(hostName);
            this.modelControllerClient = ModelControllerClient.Factory.create(inetAddress, port, getCallbackHandler());
        } catch (UnknownHostException e) {
            throw new RuntimeException("Cannot create model controller client for host: " + hostName + " and port " + port, e);
        }
    }

}
