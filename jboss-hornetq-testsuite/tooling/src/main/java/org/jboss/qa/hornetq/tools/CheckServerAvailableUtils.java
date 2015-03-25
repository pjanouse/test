package org.jboss.qa.hornetq.tools;

import org.apache.log4j.Logger;
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
            socket.connect(new InetSocketAddress(ipAddress, port), 100);
            return true;
        } catch (Exception ex) {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return false;
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

}
