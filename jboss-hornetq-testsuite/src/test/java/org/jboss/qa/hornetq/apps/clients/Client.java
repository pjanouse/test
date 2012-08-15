package org.jboss.qa.hornetq.apps.clients;

import org.jboss.qa.hornetq.test.HornetQTestCaseConstants;
import org.jboss.qa.hornetq.test.JMSTools;
import javax.naming.Context;
import javax.naming.NamingException;

/**
 *
 * Parent client class.
 *
 * Creates abstract layer for creating Context for EAP 5 and EAP 6 server.
 *
 * @author  mnovak@redhat.com
 *
 */
public class Client extends Thread implements HornetQTestCaseConstants  {

    private String currentContainer = EAP6_CONTAINER;
    private String connectionFactoryJndiName = CONNECTION_FACTORY_JNDI_EAP6;

    /**
     * Creates client for the given container.
     *
     * @param currentContainerForTest currentContainerForTest - can be "EAP 5, EAP 6"
     */
    public Client(String currentContainerForTest) {

        if (EAP5_CONTAINER.equals(currentContainerForTest)) {
            currentContainer = EAP5_CONTAINER;
            connectionFactoryJndiName = CONNECTION_FACTORY_JNDI_EAP5;
        } else {
            currentContainer = EAP6_CONTAINER;
            connectionFactoryJndiName = CONNECTION_FACTORY_JNDI_EAP6;
        }
    }

    /**
     *  Returns jndi context.
     *
     * @param hostname hostname
     * @param port port
     * @return Context
     * @throws NamingException
     */
    protected Context getContext(String hostname, int port) throws NamingException {

        Context context;

        if (currentContainer.equals(EAP5_CONTAINER)) {
            context = JMSTools.getEAP5Context(hostname, port);
        } else {
            context = JMSTools.getEAP6Context(hostname, port);
        }

        return context;
    }

    public String getConnectionFactoryJndiName() {
        return connectionFactoryJndiName;
    }

}
