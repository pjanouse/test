package org.jboss.qa.hornetq.apps.jmx;

import org.hornetq.api.core.management.HornetQServerControl;
import org.hornetq.api.jms.management.JMSServerControl;
import org.jboss.qa.hornetq.tools.ContainerInfo;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import java.io.IOException;

/**
 * Created by mnovak on 3/17/15.
 */
public interface JmxUtils {
    JMXConnector getJmxConnectorForEap(ContainerInfo container) throws IOException;

    JMXConnector getJmxConnectorForEap(String host, int port) throws IOException;

    HornetQServerControl getHornetQServerMBean(MBeanServerConnection mbeanServer) throws Exception;

    JMSServerControl getJmsServerMBean(MBeanServerConnection mbeanServer) throws Exception;

    Object getHornetQMBean(MBeanServerConnection mbeanServer, ObjectName mbeanName,
                           Class<?> mbeanClass);
}
