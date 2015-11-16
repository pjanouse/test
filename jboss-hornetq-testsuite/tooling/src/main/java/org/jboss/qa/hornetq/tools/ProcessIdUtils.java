package org.jboss.qa.hornetq.tools;

import org.apache.log4j.Logger;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.qa.hornetq.Container;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;

import org.jboss.qa.hornetq.DomainNode;


/**
 * Created by mnovak on 3/18/15.
 */
public class ProcessIdUtils {

    private static final Logger log = Logger.getLogger(ProcessIdUtils.class);

    /**
     * @return pid of the server
     */
    public static long getProcessId(Container container) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("read-resource");
        model.get(ClientConstants.OP_ADDR).add("core-service", "platform-mbean");
        model.get(ClientConstants.OP_ADDR).add("type", "runtime");

        try {
            ModelNode result = ModelNodeUtils.applyOperation(container.getHostname(), container.getPort(), model);
            String nodeName = result.get("result").get("name").asString();
            return Long.valueOf(nodeName.substring(0, nodeName.indexOf("@")));
        } catch (Exception e) {
            throw new RuntimeException("Error while reading PID failed " + container.getName(), e);
        }
    }

    public static int getProcessId(Process process) {

        int pid = 0;
        if (process.getClass().getName().equals("java.lang.UNIXProcess")) {
            try {
                Field f = process.getClass().getDeclaredField("pid");
                f.setAccessible(true);
                pid = f.getInt(process);
            } catch (Throwable e) {
            }
        } else {
            throw new IllegalStateException("This is non unis process. Implement this method for other OS.");
        }
        return pid;
    }

    /**
     * priority can be set -19 to 20, lower number = higher priority
     * @param pid
     * @param priority
     */
    public static void setPriorityToProcess(String pid, int priority) throws Exception {
        if (!System.getProperty("os.name").contains("Linux")) {
            throw new UnsupportedOperationException("Command renice which is used to lower priority of process is supported only on linux. " +
                    "Current operation system is: " + System.getProperty("os.name").contains("Linux"));
        }
        String cmd = "renice -n " + priority + " -p " + pid;
        log.info("Command: " + cmd);
        Runtime.getRuntime().exec(cmd);
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
