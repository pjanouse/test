// TODO REFACTOR FOR EAP 7
package org.jboss.qa.hornetq.apps.jmx;

import java.io.IOException;

import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.hornetq.api.core.management.HornetQServerControl;
import org.hornetq.api.core.management.ObjectNameBuilder;
import org.hornetq.api.jms.management.JMSServerControl;
import org.jboss.qa.hornetq.tools.ContainerInfo;
import org.kohsuke.MetaInfServices;


/**
 * Utility class helping with connecting to HornetQ JMX beans.
 * <p/>
 * Remember to enable JMX beans in hornetq server - set jmx-management-enabled to true
 */
@MetaInfServices
public class JmxUtilsImplEAP7 implements JmxUtils {

    /**
     * Create JMX connector to EAP 6 server.
     * <p/>
     * Use {@link JMXConnector#connect()} to get MBeanServerConnection that you can use to get MBeans. Remember to properly
     * close the connector once you're done with it!
     *
     * @param container Info about EAP 6 container.
     * @return Active JMX connector to EAP server.
     * @throws IOException
     */
    @Override
    public JMXConnector getJmxConnectorForEap(final ContainerInfo container) throws IOException {
        return getJmxConnectorForEap(container.getIpAddress(), 9999 + container.getPortOffset());
    }

    /**
     * Create JMX connector to EAP 6 server.
     * <p/>
     * Use {@link JMXConnector#connect()} to get MBeanServerConnection that you can use to get MBeans. Remember to properly
     * close the connector once you're done with it!
     *
     * @param host EAP server hostname.
     * @param port EAP server management port (9999 by default).
     * @return Active JMX connector to EAP server.
     * @throws IOException
     */
    @Override
    public JMXConnector getJmxConnectorForEap(final String host, final int port) throws IOException {
        JMXServiceURL beanServerUrl = new JMXServiceURL("service:jmx:remoting-jmx://" + host + ":" + port);
        return JMXConnectorFactory.connect(beanServerUrl);
    }

    @Override
    public HornetQServerControl getHornetQServerMBean(final MBeanServerConnection mbeanServer) throws Exception {
        return (HornetQServerControl) getHornetQMBean(mbeanServer, ObjectNameBuilder.DEFAULT.getHornetQServerObjectName(),
                HornetQServerControl.class);
    }

    @Override
    public JMSServerControl getJmsServerMBean(final MBeanServerConnection mbeanServer) throws Exception {
        return (JMSServerControl) getHornetQMBean(mbeanServer, ObjectNameBuilder.DEFAULT.getJMSServerObjectName(),
                JMSServerControl.class);
    }

    @Override
    public Object getHornetQMBean(final MBeanServerConnection mbeanServer, final ObjectName mbeanName,
                                  final Class<?> mbeanClass) {
        return MBeanServerInvocationHandler.newProxyInstance(mbeanServer, mbeanName, mbeanClass, false);
    }

}

