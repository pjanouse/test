package org.jboss.qa.hornetq.tools;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import java.lang.management.ManagementFactory;

/**
 * Created by mnovak on 3/18/15.
 */
public class ProcessIdUtils {

    private static final Logger log = Logger.getLogger(ProcessIdUtils.class);

    /**
     * Returns -1 when server is not up.
     *
     * @return pid of the server
     */
    public static long getProcessId(Container container) {

        long pid;

        try {
            JMXConnector jmxConnector = container.getJmxUtils().getJmxConnectorForEap(container.getHostname(), container.getPort());

            MBeanServerConnection mbsc =
                    jmxConnector.getMBeanServerConnection();
            ObjectName oname = new ObjectName(ManagementFactory.RUNTIME_MXBEAN_NAME);

            // form process_id@hostname (f.e. 1234@localhost)
            String runtimeName = (String) mbsc.getAttribute(oname, "Name");

            pid = Long.valueOf(runtimeName.substring(0, runtimeName.indexOf("@")));
        } catch (Exception ex)  {
            throw new RuntimeException("Getting process id failed: ", ex);
        }

        return pid;
    }

}
