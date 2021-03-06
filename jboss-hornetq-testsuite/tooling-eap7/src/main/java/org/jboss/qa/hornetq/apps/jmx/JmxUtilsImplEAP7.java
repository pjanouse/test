package org.jboss.qa.hornetq.apps.jmx;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.Container;
import org.kohsuke.MetaInfServices;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.jboss.qa.hornetq.JMSTools.isIpv6Address;


/**
 * Utility class helping with connecting to HornetQ JMX beans.
 * <p/>
 * Remember to enable JMX beans in hornetq server - set jmx-management-enabled to true
 */
@MetaInfServices
public class JmxUtilsImplEAP7 implements JmxUtils {

    private static final Logger log = Logger.getLogger(JmxUtilsImplEAP7.class);

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
        JMXServiceURL beanServerUrl;
        if (isIpv6Address(host)) {
            //beanServerUrl = new JMXServiceURL("service:jmx:remote+http://[" + host + "]:" + port);
            beanServerUrl = new JMXServiceURL("service:jmx:http-remoting-jmx://[" + host + "]:" + port);
        } else {
//            beanServerUrl = new JMXServiceURL("service:jmx:remote+http://" + host + ":" + port);
            beanServerUrl = new JMXServiceURL("service:jmx:http-remoting-jmx://" + host + ":" + port);
        }
        return JMXConnectorFactory.connect(beanServerUrl);
    }

    @Override
    public <T> T getServerMBean(MBeanServerConnection mbeanServer, Class<T> mbeanType) throws Exception {
        ObjectNameBuilder objectNameBuilder = getObjectNameBuilder(ObjectNameBuilder.class);
        return (T) getHornetQMBean(mbeanServer, objectNameBuilder.getActiveMQServerObjectName(),
                mbeanType);
    }

    @Override
    public <T> T getJmsServerMBean(MBeanServerConnection mbeanServer, Class<T> jmsServerMbeanType) throws Exception {
        ObjectNameBuilder objectNameBuilder = getObjectNameBuilder(ObjectNameBuilder.class);
        return (T) getHornetQMBean(mbeanServer, objectNameBuilder.getJMSServerObjectName(),
                jmsServerMbeanType);
    }

    @Override
    public Object getHornetQMBean(final MBeanServerConnection mbeanServer, final ObjectName mbeanName,
                                  final Class<?> mbeanClass) {
        return MBeanServerInvocationHandler.newProxyInstance(mbeanServer, mbeanName, mbeanClass, false);
    }

    @Override
    public <T> T getObjectNameBuilder(Class<T> builderType) {
        Class builderClass = ObjectNameBuilder.class;

        try {
            Method method = builderClass.getMethod("create", String.class, String.class, boolean.class);
            return (T) method.invoke(null, ActiveMQDefaultConfiguration.getDefaultJmxDomain(), "default", true);
        } catch (NoSuchMethodException e) {
            return (T) ObjectNameBuilder.DEFAULT;
        } catch (InvocationTargetException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }

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

