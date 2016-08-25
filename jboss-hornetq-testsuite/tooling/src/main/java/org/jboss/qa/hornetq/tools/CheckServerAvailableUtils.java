package org.jboss.qa.hornetq.tools;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.Container;
import org.junit.Assert;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Created by mnovak on 3/18/15.
 */
public class CheckServerAvailableUtils {

    private static final Logger log = Logger.getLogger(CheckServerAvailableUtils.class);

    /**
     * Returns true if something is listenning on server
     *
     * @param container
     */
    public static boolean checkThatServerIsReallyUp(Container container) {
        return checkThatServerIsReallyUp(container.getHostname(), container.getPort());
    }

    /**
     * Returns true if something is listenning on server
     *
     * @param ipAddress ipAddress
     * @param port      port
     */
    public static boolean checkThatServerIsReallyUp(String ipAddress, int port) {
        log.debug("Check that port is open - IP address: " + ipAddress + " port: " + port);
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(ipAddress, port), 150);
            return true;
        } catch (Exception ex) {
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Ping the given port until it's open. This method is used to check whether HQ started on the given port.
     * For example after failover/failback.
     *
     * @param ipAddress ipAddress
     * @param port      port
     * @param timeout   timeout
     */
    public static boolean waitHornetQToAlive(String ipAddress, int port, long timeout) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (!CheckServerAvailableUtils.checkThatServerIsReallyUp(ipAddress, port) && System.currentTimeMillis() - startTime < timeout) {
            Thread.sleep(1000);
        }

        if (!CheckServerAvailableUtils.checkThatServerIsReallyUp(ipAddress, port)) {
            Assert.fail("Server: " + ipAddress + ":" + port + " did not start again. Time out: " + timeout);
        }
        return CheckServerAvailableUtils.checkThatServerIsReallyUp(ipAddress, port);
    }

    public static boolean waitHornetQToAlive(Container container, long timeout) throws InterruptedException {
        return waitHornetQToAlive(container.getHostname(), container.getPort(), timeout);
    }

    public static boolean waitForLiveServerToReload(String ipAddress, int port, long timeout) {

        long start = System.currentTimeMillis();
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).setEmptyList();
        operation.get(ClientConstants.OP).set(ClientConstants.READ_ATTRIBUTE_OPERATION);
        operation.get(ClientConstants.NAME).set("server-state");

        while (System.currentTimeMillis() - start < timeout) {
            ModelControllerClient liveClient = null;
            try {
                liveClient = ModelControllerClient.Factory.create(ipAddress, port);
                ModelNode result = liveClient.execute(operation);
                if ("running".equals(result.get(ClientConstants.RESULT).asString())) {
                    return true;
                }
            } catch (IOException e) {
                log.info(e);
            } finally {
                if (liveClient != null) {
                    try {
                        liveClient.close();
                    } catch (IOException e) {
                        log.error("ModelControllerClient could not be closed.", e);
                    }
                }
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                log.warn(e);
            }
        }
        return false;
    }

    public static void waitForBrokerToDeactivate(Container container, long timeout) throws Exception {
        waitForBrokerToDeactivate(container, "default", timeout);
    }

    public static void waitForBrokerToDeactivate(Container container, String serverName, long timeout) throws Exception {
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < 3; i++) {
            try {
                JMSOperations jmsOperations = container.getJmsOperations();
                while (jmsOperations.isActive(serverName)) {
                    Thread.sleep(1000);
                    if (System.currentTimeMillis() - startTime > timeout) {
                        Assert.fail("Server " + container.getName() + " should be down. Timeout was " + timeout);
                    }
                }
                jmsOperations.close();
                return;
            } catch (Exception ex) {
                log.error("Exception thrown during getting isActive() from container: " + container.getName() + " number of tries: " + i, ex);
            }
        }
        // we should never get here
        throw new RuntimeException("Checking status isActive() of container " + container.getName() + " was not successful." +
                " Thus throwing exception to fail the test. Check test logs for more details.");
    }

    public static void waitForBrokerToActivate(Container container, long timeout) throws Exception {
        waitForBrokerToActivate(container, "default", timeout);
    }

    public static void waitForBrokerToActivate(Container container, String serverName, long timeout) throws Exception {
        long startTime = System.currentTimeMillis();
        log.info("Start waiting for broker in container: " + container.getName() + " - to activate");
        JMSOperations jmsOperations = container.getJmsOperations();
        while (!jmsOperations.isActive(serverName)) {
            log.info("Broker in container: " + container.getName() + " - is not active yet. Waiting time :" + (System.currentTimeMillis() - startTime) + " ms");
            Thread.sleep(1000);
            if (System.currentTimeMillis() - startTime > timeout) {
                jmsOperations.close();
                ContainerUtils.printThreadDump(container);
                Assert.fail("Server " + container.getName() + " should be up. Timeout was " + timeout);
            }
        }
        jmsOperations.close();
        log.info("Broker in container: " + container.getName() + " - is active");
    }
}
