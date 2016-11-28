package org.jboss.qa.hornetq;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.ServerPathUtils;

import java.io.File;
import java.io.IOException;

/**
 * Created by mnovak on 9/26/16.
 */
public class ShutdownHook extends Thread {

    private static final Logger log = Logger.getLogger(ShutdownHook.class);

    volatile boolean isServerKilled = false;
    long timeout = 120000;
    Container con = null;
    long pid = -1;

    ShutdownHook(long timeout, Container con, long pid) {
        this.timeout = timeout;
        this.con = con;
        this.pid = pid;
    }

    public void run() {

        if (pid < 0) {
            log.info("Shutdown hook started with pid < 0. Stopping shutdown hook now.");
            return;
        }

        long startTime = System.currentTimeMillis();
        try {
            while (CheckServerAvailableUtils.checkThatServerIsReallyUp(con.getHostname(), con.getHttpPort())
                    || CheckServerAvailableUtils.checkThatServerIsReallyUp(con.getHostname(), con.getBytemanPort())) {

                if (System.currentTimeMillis() - startTime > timeout) {
                    ContainerUtils.printThreadDump(pid, new File(ServerPathUtils.getStandaloneLogDirectory(con), con.getName() + "-thread-dump.txt"));
                    // kill server because shutdown hangs and fail test
                    try {
                        log.info("Killing the server " + con.getName() + " with PID: " + pid + " after timeout: " + timeout + " because it wasn't stopped by controller.stop()");
                        if (System.getProperty("os.name").contains("Windows")) {
                            Runtime.getRuntime().exec("taskkill /PID " + pid);
                        } else { // it's linux or Solaris
                            Runtime.getRuntime().exec("kill -9 " + pid);
                        }
                        log.info("Waiting 5 sec for OS close all ports held by container.");
                        Thread.sleep(5000);
                    } catch (IOException e) {
                        log.error("Invoking kill -9 " + pid + " failed.", e);
                    }
                    isServerKilled = true;
                    return;
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    //ignore
                }
            }
        } catch (Exception e) {
            log.error("Exception occured in shutdownHook: ", e);
        }
    }

    public boolean wasServerKilled() {
        return isServerKilled;
    }
}

