package org.jboss.qa.hornetq;


import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Iterator;
import java.util.ServiceLoader;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.naming.Context;
import javax.naming.NamingException;

import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.qa.hornetq.apps.jmx.JmxNotificationListener;
import org.jboss.qa.hornetq.apps.jmx.JmxUtils;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.HornetQAdminOperationsEAP6;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.ProcessIdUtils;
import org.jboss.qa.hornetq.tools.journal.JournalExportImportUtils;
import org.junit.Assert;
import org.kohsuke.MetaInfServices;


@MetaInfServices
public class ContainerEAP6 implements Container {

    private static final Logger log = Logger.getLogger(ContainerEAP6.class);

    private JmxUtils jmxUtils = null;
    private JournalExportImportUtils journalExportImportUtils = null;

    private int containerIndex = 0;
    private ContainerDef containerDef = null;
    private ContainerController containerController = null;
    private Deployer deployer = null;

    private Integer portOffset = null;


    @Override
    public void init(String containerName, int containerIndex, ArquillianDescriptor arquillianDescriptor,
            ContainerController containerController, Deployer deployer) {

        this.containerIndex = containerIndex;
        this.containerController = containerController;
        this.deployer = deployer;
        this.containerDef = getContainerDefinition(containerName, arquillianDescriptor);
    }


    @Override
    public String getName() {
        return containerDef.getContainerName();
    }


    @Override
    public String getServerHome() {
        // TODO: verifyJbossHome?
        // TODO: env property instead of container property?
        return containerDef.getContainerProperties().get("jbossHome");
    }


    @Override
    public int getPort() {
        return Integer.parseInt(containerDef.getContainerProperties().get("managementPort"));
    }


    @Override
    public int getJNDIPort() {
        return 4447 + getPortOffset();
    }


    @Override
    public int getPortOffset() {
        if (portOffset == null) {
            String tmp = System.getProperty("PORT_OFFSET_" + containerIndex);
            portOffset = (tmp == null ? 0 : Integer.valueOf(tmp));
        }
        return portOffset;
    }


    @Override
    public Context getContext() throws NamingException {
        return JMSTools.getEAP6Context(getHostname(), getPort());
    }


    @Override
    public String getHostname() {
        // TODO: verify IPv6 address
        return containerDef.getContainerProperties().get("managementAddress");
    }


    @Override
    public int getHornetqPort() {
        return HornetQTestCaseConstants.PORT_HORNETQ_DEFAULT + getPortOffset();
    }


    @Override
    public int getHornetqBackupPort() {
        return HornetQTestCaseConstants.PORT_HORNETQ_BACKUP_DEFAULT + getPortOffset();
    }


    @Override
    public int getBytemanPort() {
        return HornetQTestCaseConstants.BYTEMAN_CONTAINER1_PORT;
    }


    @Override
    public HornetQTestCaseConstants.CONTAINER_TYPE getContainerType() {
        return HornetQTestCaseConstants.CONTAINER_TYPE.EAP6_CONTAINER;
    }

    @Override
    public int getHttpPort() {
        return 8080 + getPortOffset();
    }


    @Override
    public void start() {
        containerController.start(getName());
    }


    @Override
    public void stop() {

        // there is problem with calling stop on already stopped server
        // it throws exception when server is already stopped
        // so check whether server is still running and return if not
        try {
            if (!(CheckServerAvailableUtils.checkThatServerIsReallyUp(getHostname(), getHttpPort())
                    || CheckServerAvailableUtils.checkThatServerIsReallyUp(getHostname(), getBytemanPort()))) {
                containerController.kill(getName()); // call controller.kill to arquillian that server is really dead
                return;
            }
        } catch (Exception ex) {
            log.warn("Error during getting port of byteman agent.", ex);
        }

        // because of stupid hanging during shutdown in various tests - mdb failover + hq core bridge failover
        // we kill server when it takes too long
        final long pid = ProcessIdUtils.getProcessId(this);
        // timeout to wait for shutdown of server, after timeout expires the server will be killed
        final long timeout = 120000;

        Thread shutdownHook = new Thread() {
            public void run() {

                long startTime = System.currentTimeMillis();
                try {
                    while (CheckServerAvailableUtils.checkThatServerIsReallyUp(getHostname(), getHttpPort())
                            || CheckServerAvailableUtils.checkThatServerIsReallyUp(getHostname(), getBytemanPort())) {

                        if (System.currentTimeMillis() - startTime > timeout) {
                            // kill server because shutdown hangs and fail test
                            try {
                                if (System.getProperty("os.name").contains("Windows")) {
                                    Runtime.getRuntime().exec("taskkill /PID " + pid);
                                } else { // it's linux or Solaris
                                    Runtime.getRuntime().exec("kill -9 " + pid);
                                }
                            } catch (IOException e) {
                                log.error("Invoking kill -9 " + pid + " failed.", e);
                            }
                            Assert.fail("Server: " + getName() + " did not shutdown more than: " + timeout + " and will be killed.");
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
        };
        shutdownHook.start();
        containerController.stop(getName());
        try {
            containerController.kill(getName());
        } catch (Exception ex) {
            log.error("Container was not cleanly stopped. This exception is thrown from controller.kill() call after controller.stop() was called. " +
                    "Reason for this is that controller.stop() does not have to tell arquillian that server is stopped - " +
                    "controller.kill() will do that.", ex);
        }
        try {  // wait for shutdown hook to stop - otherwise can happen that immeadiate start will keep it running and fail the test
            shutdownHook.join();
        } catch (InterruptedException e) {
            // ignore
        }

        log.info("Server " + getName() + " was stopped. There is no from tracking ports (f.e.: 9999, 5445, 8080, ..." +
                ") running on its IP " + getHostname());
    }

    @Override
    public void kill() {

        log.info("Killing server: " + getName());
        try {

            long pid = ProcessIdUtils.getProcessId(this);

            if (System.getProperty("os.name").contains("Windows") || System.getProperty("os.name").contains("windows"))  { // use taskkill
                Runtime.getRuntime().exec("taskkill /f /pid " + pid);
            } else { // on all other platforms use kill -9
                Runtime.getRuntime().exec("kill -9 " + pid);
            }
        } catch (Exception ex) {
            log.warn("Container " + getName() + " could not be killed. Set debug for logging to see exception stack trace.");
            log.debug(ex);
        } finally {
            log.info("Server: " + getName() + " -- KILLED");
        }
        containerController.kill(getName());

    }

    @Override
    public void restart() {
        stop();
        start();
    }


    @Override
    public synchronized JournalExportImportUtils getExportImportUtil() {
        if (journalExportImportUtils == null) {
            ServiceLoader serviceLoader = ServiceLoader.load(JournalExportImportUtils.class);

            @SuppressWarnings("unchecked")
            Iterator<JournalExportImportUtils> iterator = serviceLoader.iterator();
            if (!iterator.hasNext()) {
                throw new RuntimeException("No implementation found for JournalExportImportUtils.");
            }
            journalExportImportUtils = iterator.next();
        }

        return journalExportImportUtils;
    }

    @Override
    public synchronized JmxUtils getJmsUtils() {
        if (jmxUtils == null) {
            ServiceLoader serviceLoader = ServiceLoader.load(JmxUtils.class);

            @SuppressWarnings("unchecked")
            Iterator<JmxUtils> iterator = serviceLoader.iterator();
            if (!iterator.hasNext()) {
                throw new RuntimeException("No implementation found for JmxUtils.");
            }
            jmxUtils = iterator.next();
        }

        return jmxUtils;
    }


    @Override
    public JMSOperations getJmsOperations() {
        HornetQAdminOperationsEAP6 eap6AdmOps = new HornetQAdminOperationsEAP6();
        eap6AdmOps.setHostname(getHostname());
        eap6AdmOps.setPort(getPort());
        eap6AdmOps.connect();
        return eap6AdmOps;
    }


    @Override
    public synchronized JmxNotificationListener createJmxNotificationListener() {
        // always create new instance
        ServiceLoader serviceLoader = ServiceLoader.load(JmxNotificationListener.class);

        @SuppressWarnings("unchecked")
        Iterator<JmxNotificationListener> iterator = serviceLoader.iterator();
        if (!iterator.hasNext()) {
            throw new RuntimeException("No implementation found for JmxUtils.");
        }
        return iterator.next();
    }


    private ContainerDef getContainerDefinition(String containerName, ArquillianDescriptor descriptor) {
        for (GroupDef groupDef : descriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                if (containerDef.getContainerName().equalsIgnoreCase(containerName)) {
                    return containerDef;
                }
            }
        }

        throw new RuntimeException("No container with name " + containerName + " found in Arquillian descriptor "
                + descriptor.getDescriptorName());
    }

}
