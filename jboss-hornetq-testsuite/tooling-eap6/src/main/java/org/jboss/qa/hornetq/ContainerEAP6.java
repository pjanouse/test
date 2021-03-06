package org.jboss.qa.hornetq;

import org.apache.commons.io.FileUtils;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.apps.jmx.JmxNotificationListener;
import org.jboss.qa.hornetq.apps.jmx.JmxUtils;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.*;
import org.jboss.qa.hornetq.tools.journal.JournalExportImportUtils;
import org.jboss.qa.hornetq.tools.journal.JournalExportImportUtilsImplEAP6;
import org.jboss.shrinkwrap.api.Archive;
import org.junit.Assert;
import org.kohsuke.MetaInfServices;

import javax.naming.Context;
import javax.naming.NamingException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

import static org.jboss.qa.hornetq.constants.Constants.*;


@MetaInfServices
public class ContainerEAP6 implements Container {

    private static final Logger log = Logger.getLogger(ContainerEAP6.class);

    private static final String EAP_VERSION_PATTERN =
            "(?i)((Red Hat )?JBoss Enterprise Application Platform - Version )(.+?)(.[a-zA-Z]+[0-9]*)";

    private JmxUtils jmxUtils = null;
    private JournalExportImportUtils journalExportImportUtils = null;
    private int containerIndex = 0;
    private ContainerDef containerDef = null;
    private ContainerController containerController = null;
    private int pid = Integer.MIN_VALUE;
    private Map<String, String> originalContainerProperties = null;

    @Override
    public void init(String containerName, int containerIndex, ArquillianDescriptor arquillianDescriptor,
                     ContainerController containerController) {

        this.containerIndex = containerIndex;
        this.containerController = containerController;
        this.containerDef = getContainerDefinition(containerName, arquillianDescriptor);
        this.originalContainerProperties = containerDef.getContainerProperties();

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
        return MANAGEMENT_PORT_DEFAULT_EAP6 + getPortOffset();
    }

    @Override
    public int getJNDIPort() {
        return JNDI_PORT_DEFAULT_EAP6 + getPortOffset();
    }

    @Override
    public int getPortOffset() {

        return (containerIndex - 1) * DEFAULT_PORT_OFFSET_INTERVAL;
    }

    @Override
    public Context getContext() throws NamingException {
        return getContext(JNDI_CONTEXT_TYPE.NORMAL_CONTEXT);
    }

    @Override
    public Context getContext(Constants.JNDI_CONTEXT_TYPE contextType) throws NamingException {
        return JMSTools.getEAP6Context(getHostname(), getJNDIPort(), contextType);
    }


    @Override
    public String getHostname() {
        // TODO: verify IPv6 address
        return containerDef.getContainerProperties().get("managementAddress");
    }


    @Override
    public int getHornetqPort() {
        return PORT_HORNETQ_DEFAULT_EAP6 + getPortOffset();
    }


    @Override
    public int getHornetqBackupPort() {
        return PORT_HORNETQ_BACKUP_DEFAULT_EAP6 + getPortOffset();
    }


    @Override
    public int getBytemanPort() {

        return DEFAULT_BYTEMAN_PORT + getPortOffset();
    }

    @Override
    public CONTAINER_TYPE getContainerType() {
        return CONTAINER_TYPE.EAP6_CONTAINER;
    }

    @Override
    public int getHttpPort() {
        return 8080 + getPortOffset();
    }

    @Override
    public int getHttpsPort() {
        return 8443 + getPortOffset();
    }

    /**
     * Return username as defined in arquillian.xml.
     *
     * @return username or null if empty
     */
    @Override
    public String getUsername() {
        return containerDef.getContainerProperties().get("username");
    }

    /**
     * Return password as defined in arquillian.xml.
     *
     * @return password or null if empty
     */
    @Override
    public String getPassword() {
        return containerDef.getContainerProperties().get("password");
    }

    /**
     * @return PID of container process
     */
    @Override
    public int getProcessId(){
        return pid;
    }

    @Override
    public void fail(FAILURE_TYPE failureType) {
        new FailureUtils().fail(this, failureType);
    }

    @Override
    public String getServerVersion() throws FileNotFoundException {
        File versionFile = new File(getServerHome(), "version.txt");
        Scanner scanner = new Scanner(new FileInputStream(versionFile));
        String eapVersionLine = scanner.nextLine();
        return eapVersionLine.replaceAll(EAP_VERSION_PATTERN, "$3").trim();
    }

    @Override
    public void deleteDataFolder() throws IOException {
        FileUtils.deleteDirectory(new File(getServerHome(), "standalone/data"));
    }

    @Override
    public void suspend() throws IOException {
        ProcessIdUtils.suspendProcess(pid);
    }

    @Override
    public void resume() throws IOException {
        // we cannot get process id from suspended process calling in CLI, we have to know it already
        ProcessIdUtils.resumeProcess(pid);
    }

    @Override
    public void start() {
        // modify properties for arquillian.xml
        // set port off set based on how it was configured here
        // -Djboss.socket.binding.port-offset=${PORT_OFFSET_1} add to vmarguments
        // replace 9091 for byteman port

        Map<String, String> containerProperties = DefaultContainerConfigurationUtil.getOriginalContainerProperties(containerDef, containerIndex);


        containerProperties.put("managementPort", String.valueOf(getPort()));
        containerProperties.put("adminOnly", "false");

        String javaVmArguments = containerProperties.get("javaVmArguments");
        if (javaVmArguments == null) {
            javaVmArguments = "";
        }
        javaVmArguments = javaVmArguments.concat(" -Djboss.socket.binding.port-offset=" + getPortOffset());
        javaVmArguments = javaVmArguments.concat(" -Djboss.messaging.group.address=" + MCAST_ADDRESS);
        javaVmArguments = javaVmArguments.concat(" -Djboss.default.multicast.address=" + MCAST_ADDRESS);
        javaVmArguments = javaVmArguments.replace(String.valueOf(DEFAULT_BYTEMAN_PORT), String.valueOf(getBytemanPort()));
        containerProperties.put("javaVmArguments", javaVmArguments);

        start(containerProperties, 0);
    }

    @Override
    public void start(int startTimeout) {
        log.warn("Start with timeout is not supported for EAP6. Starting without timeout.");
        start();
    }

    @Override
    public void start(final Map<String, String> containerProperties, int timeout) {
        final Container container = this;

        Thread startServerThread = new Thread() {
            public void run() {
                containerController.start(container.getName(), containerProperties);
                try {
                    CheckServerAvailableUtils.waitUntilEAPStarts(container, 30000);
                } catch (Exception e) {
                    log.error("EAP doesn't start", e);
                }
            }
        };

        startServerThread.start();

        try {
            startServerThread.join(timeout);
            if (startServerThread.isAlive()) {
                // here print thread dump
                pid = ProcessIdUtils.getProcessIdOfNotFullyStartedContainer(container);
                ContainerUtils.printThreadDump(pid,
                        new File(container.getServerHome(), "startup-thread-dump.txt"));

                throw new RuntimeException("Start of the server " + container.getName() + " was not successful. Check thread dump in server home directory.");
            }
            startServerThread.join();
            pid = ProcessIdUtils.getProcessId(container);
        } catch (InterruptedException e) {
            // ignore
        } catch (Exception ex) {
            log.error("Error during start.", ex);
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void startAdminOnly() {
        Map<String, String> containerProperties = DefaultContainerConfigurationUtil.getOriginalContainerProperties(containerDef, containerIndex);
        containerProperties.put("adminOnly", "true");
        start(containerProperties, 0);
    }

    @Override
    public int getJGroupsTcpPort() {
        return Constants.JGROUPS_TCP_PORT_DEFAULT_EAP6 + getPortOffset();
    }


    @Override
    public void stop(boolean failTestIfServerCannotBeStopped) {

        log.info("Server " + getName() + "  is going to stop.");
        // there is problem with calling stop on already stopped server
        // it throws exception when server is already stopped
        // so check whether server is still running and return if not
        try {
            if (!(CheckServerAvailableUtils.checkThatServerIsReallyUp(getHostname(), getHttpPort())
                    || CheckServerAvailableUtils.checkThatServerIsReallyUp(getHostname(), getBytemanPort()))) {
                log.info("Server " + getName() + " is already really dead.");
                containerController.kill(getName()); // call controller.kill to arquillian that server is really dead
                log.info("Ending stopping procedure.");
                return;
            }
        } catch (Exception ex) {
            log.warn("Error during stop() of container. It was not possible to check whether server is running.", ex);
        }

        // timeout to wait for shutdown of server, after timeout expires the server will be killed
        final long timeout = 120000;
        final Container con = this;

        // if pid is set to value < 0 and server is running then try to get new pid
        if (pid < 0) {
            log.info("Server " + this.getName() + " is running but pid is less than 0. PID: " + pid + " -- try to get pid of running server.");
            try {
                pid = ProcessIdUtils.getProcessId(this);
            } catch (Exception ex) {
                log.warn("Server " + this.getName() + " is running but it's not possible get its PID as PID of running container", ex);
            }
            if (pid < 0) {
                try {
                    pid = ProcessIdUtils.getProcessIdOfNotFullyStartedContainer(this);
                } catch (Exception ex) {
                    log.warn("Server " + this.getName() + " is running but it's not possible get its PID of not fully started container", ex);
                }
            }

            if (pid < 0)
                Assert.fail("Server - " + this.getName() + " - is running but it's not possible get its PID. Failing the test. " +
                        "Check logs for stacktrace why the pid could not be obtained.");

        }
        ShutdownHook shutdownHook = new ShutdownHook(timeout, con, pid);
        shutdownHook.start();
        log.info("Stopping the server - " + getName());
        containerController.stop(getName());
        try {
            containerController.kill(getName());
        } catch (Exception ex) {
            log.error("Container - " + getName() + " was not cleanly stopped. This exception is thrown from controller.kill() call after controller.stop() was called. " +
                    "Reason for this is that controller.stop() does not have to tell arquillian that server is stopped - " +
                    "controller.kill() will do that.", ex);
        }
        try {  // wait for shutdown hook to stop - otherwise can happen that immeadiate start will keep it running and fail the test
            shutdownHook.join();
            // fail test which called this stop()
            if (failTestIfServerCannotBeStopped) {
                Assert.assertFalse("Server - " + con.getName() + " - did not stop in specified timeout and had to be killed. " +
                        "Check archived log directory where is thread dump.", shutdownHook.wasServerKilled());
            }
        } catch (InterruptedException e) {
            // ignore
        }

        log.info("Server " + getName() + " was stopped. There is no from tracking ports (f.e.: 9999, 5445, 8080, ..." +
                ") running on its IP " + getHostname());
    }

    @Override
    public void stop() {
        stop(true);
    }

    @Override
    public void kill() {

        log.info("Killing server: " + getName());
        try {

            ProcessIdUtils.killProcess(pid);

        } catch (Exception ex) {
            log.warn("Container " + getName() + " could not be killed. Set debug for logging to see exception stack trace.");
            log.debug(ex);
        } finally {
            log.info("Server: " + getName() + " -- KILLED");
        }
        containerController.kill(getName());

    }

    @Override
    public void waitForKill() {
        waitForKill(120000);
    }

    @Override
    public void waitForKill(long timeout) {

        long startTime = System.currentTimeMillis();

        while (CheckServerAvailableUtils.checkThatServerIsReallyUp(getHostname(), getHttpPort())
                || CheckServerAvailableUtils.checkThatServerIsReallyUp(getHostname(), getBytemanPort())) {

            if (System.currentTimeMillis() - startTime > timeout) {
                Assert.fail("Server: " + getName() + " was not killed in timeout: " + timeout);
            }
        }
    }

    @Override
    public void restart() {
        stop();
        start();
    }


    @Override
    public synchronized JournalExportImportUtils getExportImportUtil() {
        return new JournalExportImportUtilsImplEAP6(this);
    }

    @Override
    public synchronized JmxUtils getJmxUtils() {
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

    public void deploy(Archive archive) {

        undeploy(archive.getName(),false);

        HornetQAdminOperationsEAP6 eap6AdmOps = new HornetQAdminOperationsEAP6();
        try {
            eap6AdmOps.setHostname(getHostname());
            eap6AdmOps.setPort(getPort());
            eap6AdmOps.connect();
            eap6AdmOps.deploy(archive);
        } catch (Exception ex) {
            log.error("Could not deploy archive " + archive.getName() + " to node " + this.getName(), ex);
            throw new RuntimeException(ex);
        } finally {
            eap6AdmOps.close();
        }
    }

    @Override
    public void undeploy(Archive archive) {
        undeploy(archive.getName());
    }

    @Override
    public void undeploy(String archiveName) {
        undeploy(archiveName, true);
    }

    private void undeploy(String archiveName, boolean logErrorWhenUndeployFails)  {
        HornetQAdminOperationsEAP6 eap6AdmOps = new HornetQAdminOperationsEAP6();
        try {
            eap6AdmOps.setHostname(getHostname());
            eap6AdmOps.setPort(getPort());
            eap6AdmOps.connect();
            eap6AdmOps.undeploy(archiveName);
        } catch (Exception ex) {
            if (logErrorWhenUndeployFails) {
                log.error("Could not undeploy archive " + archiveName + " to node " + this.getName(), ex);
            }
        } finally {
            eap6AdmOps.close();
        }
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
    public void update(ContainerController controller) {
        if (containerController == null) {
            this.containerController = controller;
        }
    }

    @Override
    public ContainerDef getContainerDefinition() {
        return containerDef;
    }

    @Override
    public PrintJournal getPrintJournal() {
        return new PrintJournalImplEAP6(this);
    }

    @Override
    public String getConnectionFactoryName() {
        return CONNECTION_FACTORY_JNDI_EAP6;
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

    @Override
    public void setServerProfile(String name){
        containerDef.overrideProperty("serverConfig", name);
    }

    private ContainerDef getContainerDefinition(String containerName, ArquillianDescriptor descriptor) {
        for (GroupDef groupDef : descriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                if (containerDef.getContainerName().equalsIgnoreCase(containerName)) {
                    return containerDef;
                }
            }
        }

        // this is for domain mode, since there's no container group defined in arquillian.xml
        for (ContainerDef containerDef : descriptor.getContainers()) {
            if (containerDef.getContainerName().equalsIgnoreCase(containerName)) {
                return containerDef;
            }
        }

        throw new RuntimeException("No container with name " + containerName + " found in Arquillian descriptor "
                + descriptor.getDescriptorName());
    }

    private Map<String, String> getOriginalContainerProperties() {
        Map<String, String> properties = new HashMap<String, String>();

        if (originalContainerProperties != null) {
            for (String key : originalContainerProperties.keySet()) {
                properties.put(key, originalContainerProperties.get(key));
            }
        }
        return properties;
    }

}
