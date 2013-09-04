package org.jboss.qa.tools;


import java.io.IOException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import org.jboss.qa.hornetq.test.HornetQTestCase;


/**
 * Class for getting informations about HornetQ JMS server from JBoss' MBeans.
 *
 * Warning: Only works for EAP6.
 *
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public class JmsServerInfo {

    private final String hostname;

    private final int managementPort;

    private final String hornetqServerName;


    public JmsServerInfo() {
        this(HornetQTestCase.CONTAINER1_IP, HornetQTestCase.MANAGEMENT_PORT_EAP6, "default");
    }


    public JmsServerInfo(final String hostname, final int managementPort) {
        this(hostname, managementPort, "default");
    }


    public JmsServerInfo(final String hostname, final int managementPort, final String hornetqServerName) {
        this.hostname = hostname;
        this.managementPort = managementPort;
        this.hornetqServerName = hornetqServerName;
    }


    /**
     * Returns array of connections IDs of the connected clients.
     *
     * Note: You will probably get 1 more result than you expected, because the JMX client itself has its own
     * connection id.
     *
     * @return Array of client IDs.
     */
    public String[] getConnectionIds() throws IOException, InstanceNotFoundException, MBeanException,
            MalformedObjectNameException, ReflectionException {

        return (String[]) this.getMBeanServerConnection().invoke(this.getServerControlObjectName(),
                Operations.LIST_CONNECTION_IDS.getOperationName(), new Object[]{}, new String[]{});
    }


    private MBeanServerConnection getMBeanServerConnection() throws IOException {
        JMXServiceURL mbeanServerUrl = new JMXServiceURL(this.getConnectionUrl());
        JMXConnector connector = JMXConnectorFactory.connect(mbeanServerUrl);
        return connector.getMBeanServerConnection();
    }


    private ObjectName getServerControlObjectName() throws MalformedObjectNameException {
        return new ObjectName("jboss.as:hornetq-server=" + this.hornetqServerName + ",subsystem=messaging");
    }


    private String getConnectionUrl() {
        return "service:jmx:remoting-jmx://" + this.hostname + ":" + this.managementPort;
    }


    private enum Operations {

        LIST_CONNECTION_IDS("listConnectionIds");

        private final String name;


        private Operations(final String name) {
            this.name = name;
        }


        public String getOperationName() {
            return this.name;
        }

    }

}
