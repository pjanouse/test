// TODO REFACTOR FOR EAP 7
package org.jboss.qa.hornetq.apps.jmx;

import java.io.IOException;
import java.lang.management.ManagementFactory;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.hornetq.api.core.management.HornetQServerControl;
import org.hornetq.api.core.management.ObjectNameBuilder;
import org.hornetq.api.jms.management.JMSServerControl;
import org.jboss.qa.hornetq.Container;
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
    public JMXConnector getJmxConnectorForEap(final Container container) throws IOException {
        return getJmxConnectorForEap(container.getHostname(), container.getPort());
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
        JMXServiceURL beanServerUrl = new JMXServiceURL("service:jmx:http-remoting-jmx://" + host + ":" + port);
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



    public static void main(String[] args) throws IOException, MalformedObjectNameException, AttributeNotFoundException, MBeanException, ReflectionException, InstanceNotFoundException {
        JmxUtilsImplEAP7 jmxUtilsImplEAP7 = new JmxUtilsImplEAP7();
        JMXConnector jmxConnector = jmxUtilsImplEAP7.getJmxConnectorForEap("localhost", 9990);

        MBeanServerConnection mbsc =
                jmxConnector.getMBeanServerConnection();
        ObjectName oname = new ObjectName(ManagementFactory.RUNTIME_MXBEAN_NAME);

        // form process_id@hostname (f.e. 1234@localhost)
        String runtimeName = (String) mbsc.getAttribute(oname, "Name");

        long pid = Long.valueOf(runtimeName.substring(0, runtimeName.indexOf("@")));

        System.out.println(pid);
    }
}

