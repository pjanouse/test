package org.jboss.qa.hornetq.tools;

import org.apache.log4j.Logger;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.qa.hornetq.Container;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import java.lang.management.ManagementFactory;
import org.jboss.qa.hornetq.DomainNode;


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

    /**
     * @param container domain node
     * @return PID of the domain node process.
     */
    public static long getProcessId(DomainNode container) {

        /* In domain, we have to go through domain controller and get the information there, since it's not
         * possible to connect JMX directly to domain node.
         *
         * When the node is up, path /host=master/server=[name]/core-service=platform-bean/type=runtime
         * on the domain controller contains the output of the node's runtime MBean, which is what we need.
         */

        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("read-resource");
        model.get(ClientConstants.OP_ADDR).add("host", "master");
        model.get(ClientConstants.OP_ADDR).add("server", container.getName());
        model.get(ClientConstants.OP_ADDR).add("core-service", "platform-mbean");
        model.get(ClientConstants.OP_ADDR).add("type", "runtime");

        try {
            // hardcode management port number, since we have only domain controller to use
            ModelNode result = ModelNodeUtils.applyOperation(container.getHostname(), 9999, model);
            String nodeName = result.get("result").get("name").asString();
            return Long.valueOf(nodeName.substring(0, nodeName.indexOf("@")));
        } catch (Exception e) {
            throw new RuntimeException("Error while reading PID of domain node " + container.getName(), e);
        }
    }

}
