package org.jboss.qa.hornetq;

import org.jboss.qa.hornetq.tools.ContainerUtils;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;


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

    private final String messagingServerName;

    private Container container;


    public JmsServerInfo(Container container){
        this(container, "default");
    }
    public JmsServerInfo(Container container, String serverName){
        this.hostname=container.getHostname();
        this.managementPort= container.getPort()    ;
        this.messagingServerName = serverName;
        this.container=container;
    }


    /**
     * Returns array of connections IDs of the connected org.jboss.qa.hornetq.apps.clients.
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
        if(ContainerUtils.isEAP6(container)){
            return new ObjectName("jboss.as:hornetq-server=" + this.messagingServerName + ",subsystem=messaging");
        }else{
            return new ObjectName("jboss.as:server=" + this.messagingServerName + ",subsystem=messaging-activemq");
        }

    }


    private String getConnectionUrl() {
        if(ContainerUtils.isEAP6(container)){
            return "service:jmx:remoting-jmx://" + this.hostname + ":" + this.managementPort;
        }else{
            return "service:jmx:http-remoting-jmx://" + this.hostname + ":" + this.managementPort;
        }

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
