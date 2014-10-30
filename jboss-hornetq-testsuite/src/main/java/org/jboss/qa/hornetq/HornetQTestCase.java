package org.jboss.qa.hornetq;

import org.junit.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.test.spi.event.suite.BeforeSuite;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.servlets.KillerServlet;
import org.jboss.qa.hornetq.junit.rules.ArchiveServerLogsAfterFailedTest;
import org.jboss.qa.hornetq.tools.*;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;

import javax.naming.Context;
import javax.naming.NamingException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Parent class for all HornetQ test cases. Provides an abstraction of used container
 *
 * @author pslavice@redhat.com
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
public class HornetQTestCase implements ContextProvider, HornetQTestCaseConstants {

    @Rule
    public ArchiveServerLogsAfterFailedTest ruleExample = new ArchiveServerLogsAfterFailedTest();

    // Logger
    private static final Logger log = Logger.getLogger(HornetQTestCase.class);

    // JBOSS_HOME properties
    public final static String JBOSS_HOME_1;
    public final static String JBOSS_HOME_2;
    public final static String JBOSS_HOME_3;
    public final static String JBOSS_HOME_4;

    // IP address for containers
    public final static String DEFAULT_CONTAINER_IP = "127.0.0.1";
    public final static String CONTAINER1_IP;
    public final static String CONTAINER2_IP;
    public final static String CONTAINER3_IP;
    public final static String CONTAINER4_IP;

    // get port.offset.x properties from pom.xml
    public final static int PORT_OFFSET_1;
    public final static int PORT_OFFSET_2;
    public final static int PORT_OFFSET_3;
    public final static int PORT_OFFSET_4;

    // Multi-cast address
    public static final String MCAST_ADDRESS;

    // Journal directory for first live/backup pair or first node in cluster
    public static final String JOURNAL_DIRECTORY_A;

    // Journal directory for second live/backup pair or second node in cluster
    public static final String JOURNAL_DIRECTORY_B;

    // Defined deployments - killer servlet which kills server
    // Artifact is not deployed on the server automatically, it is necessary to deploy it manually
    protected static final String SERVLET_KILLER_1 = "killerServlet1";
    protected static final String SERVLET_KILLER_2 = "killerServlet2";
    protected static final String SERVLET_KILLER_3 = "killerServlet3";
    protected static final String SERVLET_KILLER_4 = "killerServlet4";

    // Active server - EAP 5 or EAP 6?
    protected String currentContainerForTest;

    @ArquillianResource
    protected ContainerController controller;

    @ArquillianResource
    protected Deployer deployer;

    // this property is initialized during BeforeClass phase by ArquillianConfiguration extension
    private static ArquillianDescriptor arquillianDescriptor;

    static {
        // Path to the journal
        String tmpJournalA = System.getenv("JOURNAL_DIRECTORY_A");
        JOURNAL_DIRECTORY_A = (tmpJournalA != null) ? tmpJournalA : "../../../../hornetq-journal-A";
        String tmpJournalB = System.getenv("JOURNAL_DIRECTORY_B");
        JOURNAL_DIRECTORY_B = (tmpJournalB != null) ? tmpJournalB : "../../../../hornetq-journal-B";

        // IP addresses for the servers
        CONTAINER1_IP = checkIPv6Address(getEnvProperty("MYTESTIP_1") == null ? DEFAULT_CONTAINER_IP : getEnvProperty("MYTESTIP_1"));
        CONTAINER2_IP = checkIPv6Address(getEnvProperty("MYTESTIP_2") == null ? DEFAULT_CONTAINER_IP : getEnvProperty("MYTESTIP_2"));
        CONTAINER3_IP = checkIPv6Address(getEnvProperty("MYTESTIP_3") == null ? DEFAULT_CONTAINER_IP : getEnvProperty("MYTESTIP_3"));
        CONTAINER4_IP = checkIPv6Address(getEnvProperty("MYTESTIP_4") == null ? DEFAULT_CONTAINER_IP : getEnvProperty("MYTESTIP_4"));
        // if MCAST_ADDR is null then generate multicast address
        String tmpMultiCastAddress = System.getProperty("MCAST_ADDR");
        MCAST_ADDRESS = tmpMultiCastAddress != null ? tmpMultiCastAddress :
                new StringBuilder().append(randInt(224, 239)).append(".").append(randInt(1, 254)).append(".")
                        .append(randInt(1, 254)).append(".").append(randInt(1, 254)).toString();

        JBOSS_HOME_1 = verifyJbossHome(getEnvProperty("JBOSS_HOME_1"));
        JBOSS_HOME_2 = verifyJbossHome(getEnvProperty("JBOSS_HOME_2"));
        JBOSS_HOME_3 = verifyJbossHome(getEnvProperty("JBOSS_HOME_3"));
        JBOSS_HOME_4 = verifyJbossHome(getEnvProperty("JBOSS_HOME_4"));

        PORT_OFFSET_1 = Integer.valueOf(getEnvProperty("PORT_OFFSET_1") != null ? getEnvProperty("PORT_OFFSET_1") : "0");
        PORT_OFFSET_2 = Integer.valueOf(getEnvProperty("PORT_OFFSET_2") != null ? getEnvProperty("PORT_OFFSET_2") : "0");
        PORT_OFFSET_3 = Integer.valueOf(getEnvProperty("PORT_OFFSET_3") != null ? getEnvProperty("PORT_OFFSET_3") : "0");
        PORT_OFFSET_4 = Integer.valueOf(getEnvProperty("PORT_OFFSET_4") != null ? getEnvProperty("PORT_OFFSET_4") : "0");

    }

    // composite info objects for easier passing to utility classes
    public static final ContainerInfo CONTAINER1_INFO = new ContainerInfo(CONTAINER1, CONTAINER1_IP,
            BYTEMAN_CONTAINER1_PORT, PORT_OFFSET_1, JBOSS_HOME_1);
    public static final ContainerInfo CONTAINER2_INFO = new ContainerInfo(CONTAINER2, CONTAINER2_IP,
            BYTEMAN_CONTAINER2_PORT, PORT_OFFSET_2, JBOSS_HOME_2);
    public static final ContainerInfo CONTAINER3_INFO = new ContainerInfo(CONTAINER3, CONTAINER3_IP,
            BYTEMAN_CONTAINER3_PORT, PORT_OFFSET_3, JBOSS_HOME_3);
    public static final ContainerInfo CONTAINER4_INFO = new ContainerInfo(CONTAINER4, CONTAINER4_IP,
            BYTEMAN_CONTAINER4_PORT, PORT_OFFSET_4, JBOSS_HOME_4);

    /**
     * Stops all servers
     */
    @After
    public void stopAllServers() {
        stopServer(CONTAINER1);
        stopServer(CONTAINER2);
        stopServer(CONTAINER3);
        stopServer(CONTAINER4);
    }

    /**
     * Returns a psuedo-random number between min and max, inclusive.
     * The difference between min and max can be at most
     * <code>Integer.MAX_VALUE - 1</code>.
     *
     * @param min Minimim value
     * @param max Maximim value.  Must be greater than min.
     * @return Integer between min and max, inclusive.
     * @see java.util.Random#nextInt(int)
     */
    public static int randInt(int min, int max) {

        // Usually this can be a field rather than a method variable
        Random rand = new Random();

        // nextInt is normally exclusive of the top value,
        // so add 1 to make it inclusive
        int randomNum = rand.nextInt((max - min) + 1) + min;

        return randomNum;
    }

    /**
     * Takes path to jboss home dir and tries to fix it:
     * if path does not exist then try to jboss-eap-6.1, ...6.2, ...
     *
     * @param jbossHome jboss home
     */
    private static String verifyJbossHome(String jbossHome) {
        if (jbossHome == null) {
            throw new RuntimeException("JBossHome is null, please setup correct JBOSS_HOME");
        }
        File jbossHomeDir = new File(jbossHome);
        if (!jbossHomeDir.exists()) {
            jbossHomeDir = new File(jbossHomeDir.getAbsolutePath().replace("jboss-eap-6.0", "jboss-eap-6.1"));
        }
        return jbossHomeDir.getAbsolutePath();
    }

    /**
     * If it's IPv6 address then add [xxx::xxx] so port can be added.
     */
    private static String checkIPv6Address(String ipAddress) {

        if (ipAddress != null) {
            InetAddress ia = null;
            try {
                ia = InetAddress.getByName(ipAddress);
            } catch (UnknownHostException e) {
                log.error("Address: " + ipAddress + " cannot be found. Double check your MYTESTIP_{1..4} properties whether they're correct.", e);
            }
            if (ia instanceof Inet6Address && !ipAddress.contains("[")) {
                return "[" + ipAddress + "]";
            }
        }
        return ipAddress;
    }

    /**
     * Default constructor
     */
    public HornetQTestCase() {
        this.setCurrentContainerForTest(getCurrentContainerId());
        log.info("Setting current container ID : " + this.getCurrentContainerForTest());
    }

    /**
     * Returns and logs value of the env property
     *
     * @param name name of
     * @return value of the defined property or null
     */
    private static String getEnvProperty(String name) {
        String envProperty = null;
        if (System.getProperty(name) != null) {
            envProperty = System.getProperty(name);
            log.info(String.format("export %s='%s'", name, envProperty));
        }
        return envProperty;
    }

    protected Context getContext(String containerName) throws NamingException {
        return getContext(getHostname(containerName), getJNDIPort(containerName));
    }

    /**
     * Returns context
     *
     * @param hostName target hostname with JNDI service
     * @param port     port on the target service
     * @return instance of {@link Context}
     * @throws NamingException if a naming exception is encountered
     */
    public Context getContext(String hostName, int port) throws NamingException {
        Context ctx = null;
        if (isEAP5()) {
            ctx = getEAP5Context(hostName, port);
        } else if (isEAP6()) {
            ctx = getEAP6Context(hostName, port);
        } else {
            log.error("Cannot determine which container is used!");
        }
        return ctx;
    }

    /**
     * Returns EAP 6 context
     *
     * @param hostName host name
     * @param port     target port with the service
     * @return instance of the context
     * @throws NamingException if something goes wrong
     */
    private Context getEAP6Context(String hostName, int port) throws NamingException {
        return JMSTools.getEAP6Context(hostName, port);
    }

    /**
     * Returns EAP 5 context
     *
     * @param hostName host name
     * @param port     target port with the service
     * @return instance of the context
     * @throws NamingException if something goes wrong
     */
    private Context getEAP5Context(String hostName, int port) throws NamingException {
        return JMSTools.getEAP5Context(hostName, port);
    }

    /**
     * Returns port for JNDI service
     *
     * @return returns default port for JNDI service
     */
    public int getJNDIPort() {
        int port = 0;
        if (isEAP5() || isEAP5WithJBM()) {
            port = PORT_JNDI_EAP5;
        } else if (isEAP6()) {
            port = PORT_JNDI_EAP6;
        }
        return port;
    }

    /**
     * Returns port for JNDI service
     *
     * @return returns default port for JNDI service
     */
    public static int getJNDIPort(String containerName) {

        if (getContainerInfo(containerName).getContainerType() == CONTAINER_TYPE.EAP5_CONTAINER
                || getContainerInfo(containerName).getContainerType() == CONTAINER_TYPE.EAP5_WITH_JBM_CONTAINER) {
            return 1099 + getContainerInfo(containerName).getPortOffset();
        } else {
            return 4447 + getContainerInfo(containerName).getPortOffset();
        }
    }

    public static int getLegacyJNDIPort(String containerName) {

        return 1099 + getContainerInfo(containerName).getPortOffset();

    }

    /**
     * @see org.jboss.qa.hornetq.ContextProvider#getContext()
     */
    public Context getContext() throws NamingException {
        return getContext(getHostname(CONTAINER1), getJNDIPort(CONTAINER1));
    }

    /**
     * @see org.jboss.qa.hornetq.ContextProvider#getContextContainer1()
     */
    public Context getContextContainer1() throws NamingException {
        return getContext(getHostname(CONTAINER1), getJNDIPort(CONTAINER1));
    }

    /**
     * @see org.jboss.qa.hornetq.ContextProvider#getContextContainer2()
     */
    public Context getContextContainer2() throws NamingException {
        return getContext(getHostname(CONTAINER2), getJNDIPort(CONTAINER2));
    }

    /**
     * Returns JNDI name for the connection factory
     *
     * @return JNDI name for CNF
     */
    public String getConnectionFactoryName() {
        return (isEAP5()) ? CONNECTION_FACTORY_JNDI_EAP5 : CONNECTION_FACTORY_JNDI_EAP6;
    }

    /**
     * Returns instance of the {@link JMSOperations} for the configured container
     *
     * @return instance of the <code>JMSOperation</code>
     */
    public JMSOperations getJMSOperations() {
        return getJMSOperations(CONTAINER1);
    }

    /**
     * Returns instance of the {@link JMSOperations} for the configured container
     *
     * @param container target container name
     * @return instance of the <code>JMSOperation</code>
     */
    public JMSOperations getJMSOperations(String container) {
        JMSOperations operations = null;

        CONTAINER_TYPE container_type = getContainerInfo(container).getContainerType();

        switch (container_type) {
            case EAP5_CONTAINER:
                HornetQAdminOperationsEAP5 eap5AdmOps = new HornetQAdminOperationsEAP5();
                eap5AdmOps.setHostname(getHostname(container));
                eap5AdmOps.setProfile(getProfile(container));
                eap5AdmOps.setRmiPort(getJNDIPort(container));
                eap5AdmOps.setJbossHome(getJbossHome(container));
                operations = eap5AdmOps;
                break;
            case EAP5_WITH_JBM_CONTAINER:
                JBMAdminOperationsEAP5 eap5JbmAdmOps = new JBMAdminOperationsEAP5();
                eap5JbmAdmOps.setHostname(getHostname(container));
                eap5JbmAdmOps.setProfile(getProfile(container));
                eap5JbmAdmOps.setRmiPort(getJNDIPort(container));
                eap5JbmAdmOps.setJbossHome(getJbossHome(container));
                operations = eap5JbmAdmOps;
                break;
            default:
                HornetQAdminOperationsEAP6 eap6AdmOps = new HornetQAdminOperationsEAP6();
                eap6AdmOps.setHostname(getHostname(container));
                eap6AdmOps.setPort(getPort(container));
                eap6AdmOps.connect();
                operations = eap6AdmOps;
                break;
        }
        return operations;
    }

    /**
     * Return true if container is EAP 5 with JBM.
     *
     * @return true if EAP 5 with JBM
     */
    private boolean isEAP5WithJBM() {
        return (this.getCurrentContainerForTest() != null) && EAP5_WITH_JBM_CONTAINER.equals(this.getCurrentContainerForTest());
    }

    /**
     * Deletes given folder and all sub folders
     *
     * @param path folder which should be deleted
     * @return true if operation was successful, false otherwise
     */
    protected static boolean deleteFolder(File path) {
        if (log.isDebugEnabled()) {
            log.debug(String.format("Removing folder '%s'", path));
        }
        boolean successful = true;
        if (path.exists()) {
            File[] files = path.listFiles();
            if (files != null) {
                for (File file : files) {
                    successful = successful && ((file.isDirectory()) ? deleteFolder(file) : file.delete());
                }
            }
        }
        return successful && (path.delete());
    }

    /**
     * Deletes data folder for given JBoss home, removes standalone data folder for standalone profile
     *
     * @param jbossHome     JBoss home folder
     * @param containerName name of the target container
     * @return true if operation was successful, false otherwise
     */
    protected boolean deleteDataFolder(String jbossHome, String containerName) {
        boolean result = false;
        if (isEAP5()) {
            result = deleteFolder(new File(jbossHome + "/server/" + getProfile(containerName) + "/data"));
        } else if (isEAP6()) {
            result = deleteFolder(new File(jbossHome + "/standalone/data"));
        }
        return result;
    }

    /**
     * Deletes data folder for given JBoss home - system property JBOSS_HOME_1,
     * removes standalone data folder for standalone profile.
     *
     * @return true if operation was successful, false otherwise
     */
    protected boolean deleteDataFolderForJBoss1() {
        return deleteDataFolder(JBOSS_HOME_1, CONTAINER1);
    }

    /**
     * Deletes data folder for given JBoss home - system property JBOSS_HOME_2,
     * removes standalone data folder for standalone profile.
     *
     * @return true if operation was successful, false otherwise
     */
    protected boolean deleteDataFolderForJBoss2() {
        return deleteDataFolder(JBOSS_HOME_2, CONTAINER2);
    }

    /**
     * Sets ArquillianDescriptor via ARQ LoadableExtension
     *
     * @param event      ARQ event
     * @param descriptor instance of the description
     */
    @SuppressWarnings("unused")
    public void setArquillianDescriptor(@Observes BeforeSuite event, ArquillianDescriptor descriptor) {
        arquillianDescriptor = descriptor;
    }

    public static ArquillianDescriptor getArquillianDescriptor() {
        return arquillianDescriptor;
    }

    public void describeTestStart(@Observes org.jboss.arquillian.test.spi.event.suite.Before event) {
        log.info("Start test -------------------------------- " + event.getTestClass().getName() + "." + event.getTestMethod().getName());
    }

    public void describeTestStop(@Observes org.jboss.arquillian.test.spi.event.suite.After event) {
        log.info("Stop test -------------------------------- " + event.getTestClass().getName() + "." + event.getTestMethod().getName());
    }

    /**
     * Kills server using killer servlet. This just kill server and does not do
     * anything else. It doesn't call controller.kill
     *
     * @param ContainerName container name which should be killed
     */
    protected void killServer(String ContainerName) {
        log.info("Killing server: " + ContainerName);
        try {
            if (CONTAINER1.equals(ContainerName)) {
                killServer(CONTAINER1, SERVLET_KILLER_1, getHostname(CONTAINER1));
            } else if (CONTAINER2.equals(ContainerName)) {
                killServer(CONTAINER2, SERVLET_KILLER_2, CONTAINER2_IP);
            } else if (CONTAINER3.equals(ContainerName)) {
                killServer(CONTAINER3, SERVLET_KILLER_3, getHostname(CONTAINER3));
            } else if (CONTAINER4.equals(ContainerName)) {
                killServer(CONTAINER4, SERVLET_KILLER_4, getHostname(CONTAINER4));
            } else {
                throw new RuntimeException(
                        String.format("Name of the container %s for is not known. It can't be used", ContainerName));
            }
        } catch (Exception ex) {
            log.error("Using killer servlet failed: ", ex);
        } finally {
            log.info("Server: " + ContainerName + " -- KILLED");
        }
    }

    /**
     * Kills server using killer servlet. This just kill server and does not do
     * anything else. It doesn't call controller.kill
     *
     * @param container         name of the container which will be killed
     * @param killerServletName name of the killer servlet - deployment name
     * @param serverIP          ip address of the killed server
     * @throws Exception if something goes wrong
     */
    public void killServer(String container, String killerServletName, String serverIP) throws Exception {
        try {
            deployer.undeploy(killerServletName);
        } catch (Exception ex) {
            log.debug("Killer servlet was not deployed. Deployed it.");
        }
        deployer.deploy(killerServletName);
        String callingURLForKill = "http://" + serverIP + ":" + getHttpPort(container) + "/KillerServlet/KillerServlet?op=kill";
        log.info("Calling url to kill server: " + callingURLForKill);
        HttpRequest.get(callingURLForKill, 4, TimeUnit.SECONDS);
        Thread.sleep(3000);
        controller.kill(container);
    }

    /**
     * Returns 8080 + portOffset.
     * <p/>
     * Port offset is specified in pom.xml - PORT_OFFSET_X property.
     *
     * @param container
     * @return 8080 + portOffset
     */
    public static int getHttpPort(String container) {
        return 8080 + getContainerInfo(container).getPortOffset();
    }

    /**
     * Returns container info for given container.
     *
     * @param containerName name of the container
     * @return container info class
     */
    public static ContainerInfo getContainerInfo(String containerName) {
        if (CONTAINER1.equals(containerName)) {
            return CONTAINER1_INFO;
        } else if (CONTAINER2.equals(containerName)) {
            return CONTAINER2_INFO;
        } else if (CONTAINER3.equals(containerName)) {
            return CONTAINER3_INFO;
        } else if (CONTAINER4.equals(containerName)) {
            return CONTAINER4_INFO;
        } else {
            throw new RuntimeException(
                    String.format("Name of the container %s is not known. It can't be used", containerName));
        }
    }

    /**
     * This is wrapper method for controller.stop() method. Problem is that in EAP 5 this method throws exception
     * when server is not running. We'll swallow this exception.
     *
     * @param containerName name of the container
     */
    public void stopServer(final String containerName) {

        // there is problem with calling stop on already stopped server
        // it throws exception when server is already stopped
        // so check whether server is still running and return if not
        try {
            if (!(checkThatServerIsReallyUp(getHostname(containerName), getHttpPort(containerName))
                    || checkThatServerIsReallyUp(getHostname(containerName), getBytemanPort(containerName)))) {
                controller.kill(containerName); // call controller.kill to arquillian that server is really dead
                return;
            }
        } catch (Exception ex) {
            log.warn("Error during getting port of byteman agent.", ex);
        }

        // because of stupid hanging during shutdown in various tests - mdb failover + hq core bridge failover
        // we kill server when it takes too long
        final long pid = getProcessId(containerName);
        // timeout to wait for shutdown of server, after timeout expires the server will be killed
        final long timeout = 120000;

        Thread shutdownHook = new Thread() {
            public void run() {

                long startTime = System.currentTimeMillis();
                try {
                    while (checkThatServerIsReallyUp(getHostname(containerName), getPort(containerName))
                            || checkThatServerIsReallyUp(getHostname(containerName), getHttpPort(containerName))
                            || checkThatServerIsReallyUp(getHostname(containerName), getBytemanPort(containerName))) {

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
                            Assert.fail("Server: " + containerName + " did not shutdown more than: " + timeout + " and will be killed.");
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
        controller.stop(containerName);
        try {
            controller.kill(containerName);
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

        log.info("Server " + containerName + " was stopped. There is no from tracking ports (f.e.: 9999, 5445, 8080, ..." +
                ") running on its IP " + getHostname(containerName));
    }

    public int getBytemanPort(String containerName) throws Exception {

        return getContainerInfo(containerName).getBytemanPort();

    }

    public static int getHornetqPort(String containerName) {
        return PORT_HORNETQ_DEFAULT + getContainerInfo(containerName).getPortOffset();
    }

    /**
     * This port for collocated backup.
     *
     * @param containerName name of the container
     * @return port of collocated backup
     */
    public static int getHornetqBackupPort(String containerName) {
        return PORT_HORNETQ_BACKUP_DEFAULT + getContainerInfo(containerName).getPortOffset();
    }


    /**
     * Returns -1 when server is not up. Port 8080 is not up.
     *
     * @param containerName container name
     * @return pid of the server
     */
    public long getProcessId(String containerName) {

        if (!checkThatServerIsReallyUp(getHostname(containerName), getHttpPort(containerName))) {
            return -1;
        }

        if (CONTAINER1.equals(containerName)) {
            try {
                deployer.undeploy(SERVLET_KILLER_1);
            } catch (Exception ex) {
                log.debug("Ignore this exception: " + ex.getMessage());
            }
            try {
                deployer.deploy(SERVLET_KILLER_1);
            } catch (Exception ex) {
                log.debug("Ignore this exception: " + ex.getMessage());
            }
        } else if (CONTAINER2.equals(containerName)) {
            try {
                deployer.undeploy(SERVLET_KILLER_2);
            } catch (Exception ex) {
                log.debug("Ignore this exception: " + ex.getMessage());
            }
            try {
                deployer.deploy(SERVLET_KILLER_2);
            } catch (Exception ex) {
                log.debug("Ignore this exception: " + ex.getMessage());
            }
        } else if (CONTAINER3.equals(containerName)) {
            try {
                deployer.undeploy(SERVLET_KILLER_3);
            } catch (Exception ex) {
                log.debug("Ignore this exception: " + ex.getMessage());
            }
            try {
                deployer.deploy(SERVLET_KILLER_3);
            } catch (Exception ex) {
                log.debug("Ignore this exception: " + ex.getMessage());
            }
        } else if (CONTAINER4.equals(containerName)) {
            try {
                deployer.undeploy(SERVLET_KILLER_4);
            } catch (Exception ex) {
                log.debug("Ignore this exception: " + ex.getMessage());
            }
            try {
                deployer.deploy(SERVLET_KILLER_4);
            } catch (Exception ex) {
                log.debug("Ignore this exception: " + ex.getMessage());
            }
        } else {
            throw new RuntimeException(String.format("Name of the container %s for is not known. It can't be used", containerName));
        }

        String pid = "";
        try {
            log.info("Calling get pid: http://" + getHostname(containerName) + ":" + getHttpPort(containerName) + "/KillerServlet/KillerServlet?op=getId");
            pid = HttpRequest.get("http://" + getHostname(containerName) + ":" + getHttpPort(containerName) + "/KillerServlet/KillerServlet?op=getId", 10, TimeUnit.SECONDS);
            log.info("Pid is :" + pid);
        } catch (IOException e) {
            log.error("Error when calling killer servlet for pid.", e);
        } catch (TimeoutException e) {
            log.error("Timeout when calling killer servlet for pid.", e);
        }

        try {
            if (CONTAINER1.equals(containerName)) {
                deployer.undeploy(SERVLET_KILLER_1);
            } else if (CONTAINER2.equals(containerName)) {
                deployer.undeploy(SERVLET_KILLER_2);

            } else if (CONTAINER3.equals(containerName)) {
                deployer.undeploy(SERVLET_KILLER_3);

            } else if (CONTAINER4.equals(containerName)) {
                deployer.undeploy(SERVLET_KILLER_4);
            } else {
                throw new RuntimeException(String.format("Name of the container %s for is not known. It can't be used", containerName));
            }
        } catch (Exception ex) {
            log.debug("Killer servlet was not deployed and can't be un-deployed.");
        }

        return Long.parseLong(pid.trim());

    }

    /**
     * Creates archive with the killer server
     *
     * @return archive
     * @throws Exception if something is wrong
     */
    @Deployment(managed = false, testable = false, name = SERVLET_KILLER_1)
    @TargetsContainer(CONTAINER1)
    @SuppressWarnings("unused")
    public static WebArchive getDeploymentKilServletContainer1() throws Exception {
        return createKillerServlet();
    }

    /**
     * Creates archive with the killer server
     *
     * @return archive
     * @throws Exception if something is wrong
     */
    @Deployment(managed = false, testable = false, name = SERVLET_KILLER_2)
    @TargetsContainer(CONTAINER2)
    @SuppressWarnings("unused")
    public static WebArchive getDeploymentKilServletContainer2() throws Exception {
        return createKillerServlet();
    }

    /**
     * Creates archive with the killer server
     *
     * @return archive
     * @throws Exception if something is wrong
     */
    @Deployment(managed = false, testable = false, name = SERVLET_KILLER_3)
    @TargetsContainer(CONTAINER3)
    @SuppressWarnings("unused")
    public static WebArchive getDeploymentKilServletContainer3() throws Exception {
        return createKillerServlet();
    }

    /**
     * Creates archive with the killer server
     *
     * @return archive
     * @throws Exception if something is wrong
     */
    @Deployment(managed = false, testable = false, name = SERVLET_KILLER_4)
    @TargetsContainer(CONTAINER4)
    @SuppressWarnings("unused")
    public static WebArchive getDeploymentKilServletContainer4() throws Exception {
        return createKillerServlet();
    }

    /**
     * Prepares war artifact with the killer servlet used for killing server
     *
     * @return web archive
     */
    private static WebArchive createKillerServlet() {
        final WebArchive killerServlet = ShrinkWrap.create(WebArchive.class, "killerServlet.war");
        StringBuilder webXml = new StringBuilder();
        webXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> ");
        webXml.append("<web-app version=\"2.5\" xmlns=\"http://java.sun.com/xml/ns/javaee\" \n");
        webXml.append("         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n");
        webXml.append("         xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee \n");
        webXml.append("                              http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd\">\n");
        webXml.append(" <servlet>\n");
        webXml.append("   <servlet-name>KillerServlet</servlet-name>\n");
        webXml.append("   <servlet-class>org.jboss.qa.hornetq.apps.servlets.KillerServlet</servlet-class>\n");
        webXml.append(" </servlet>\n");
        webXml.append("\n");
        webXml.append(" <servlet-mapping>\n");
        webXml.append("   <servlet-name>KillerServlet</servlet-name>\n");
        webXml.append("   <url-pattern>/KillerServlet</url-pattern>\n");
        webXml.append(" </servlet-mapping>\n");
        webXml.append("</web-app>\n");
        webXml.append("\n");

        StringBuilder jbossWebXml = new StringBuilder();
        jbossWebXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> \n");
        jbossWebXml.append("<jboss-web> \n");
        jbossWebXml.append("  <context-root>/KillerServlet</context-root> \n");
        jbossWebXml.append("</jboss-web> \n");
        webXml.append("\n");

        killerServlet.addClass(KillerServlet.class);
        killerServlet.addAsWebInfResource(new StringAsset(webXml.toString()), "web.xml");
        killerServlet.addAsWebInfResource(new StringAsset(jbossWebXml.toString()), "jboss-web.xml");

        if (log.isTraceEnabled()) {
            log.trace(webXml.toString());
            log.trace(jbossWebXml.toString());
            log.trace(killerServlet.toString(true));
        }
        return killerServlet;
    }

    /**
     * Returns true if current container is EAP 5
     *
     * @return true for EAP 5 container
     */
    public boolean isEAP5() {
        return ((this.getCurrentContainerForTest() != null) && EAP5_CONTAINER.equals(this.getCurrentContainerForTest()));
    }

    /**
     * Returns true if current container is EAP 5
     *
     * @return true for EAP 6 container
     */
    public boolean isEAP6() {
        return (this.getCurrentContainerForTest() != null) && EAP6_CONTAINER.equals(this.getCurrentContainerForTest());
    }

    /**
     * Methods determines current container (EAP 6 or EAP 5)
     *
     * @return container id
     */
    public static String getCurrentContainerId() {
        String containerId = EAP6_CONTAINER;
        String arqConfigurationFile = System.getProperty("arquillian.xml");
        if (arqConfigurationFile != null && arqConfigurationFile.trim().length() > 0) {
            if (arqConfigurationFile.toLowerCase().contains("eap5")) {
                containerId = EAP5_CONTAINER;
                if (arqConfigurationFile.toLowerCase().contains("jbm")) {
                    containerId = EAP5_WITH_JBM_CONTAINER;
                }
            } else if (arqConfigurationFile.toLowerCase().contains("legacy")) {
                containerId = EAP6_LEGACY_CONTAINER;
            }
        }
        return containerId;
    }

    /**
     * Method blocks until all receivers gets the numberOfMessages or timeout expires
     * <p/>
     * This is NOT sum{receivers.getCount()}. Each receiver must have numberOfMessages.
     *
     * @param receivers        receivers
     * @param numberOfMessages numberOfMessages
     * @param timeout          timeout
     */
    public void waitForReceiversUntil(List<Client> receivers, int numberOfMessages, long timeout) {
        long startTimeInMillis = System.currentTimeMillis();

        for (Client c : receivers) {
            while (c.getCount() < numberOfMessages) {
                if ((System.currentTimeMillis() - startTimeInMillis) > timeout) {
                    Assert.fail("Client: " + c + " did not receive " + numberOfMessages + " in timeout: " + timeout);
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Method blocks until all receivers gets the numberOfMessages or timeout expires
     *
     * @param producers        receivers
     * @param numberOfMessages numberOfMessages
     * @param timeout          timeout
     */
    public void waitForProducersUntil(List<Client> producers, int numberOfMessages, long timeout) {
        long startTimeInMillis = System.currentTimeMillis();

        for (Client c : producers) {
            while (c.getCount() < numberOfMessages) {
                if ((System.currentTimeMillis() - startTimeInMillis) > timeout) {
                    Assert.fail("Client: " + c + " did not send " + numberOfMessages + " in timeout: " + timeout);
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static CONTAINER_TYPE getContainerType(String containerName) {
        return getContainerInfo(containerName).getContainerType();
    }

    /**
     * Gets name of the profile. Related only to EAP 5.
     *
     * @param containerName name of the container
     * @return Name of the profile as specified in arquillian.xml for
     * profileName or "default" if not set.
     */
    public static String getProfile(String containerName) {
        for (GroupDef groupDef : arquillianDescriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                if (containerDef.getContainerName().equalsIgnoreCase(containerName)) {
                    if (containerDef.getContainerProperties().containsKey("profileName")) {
                        return containerDef.getContainerProperties().get("profileName");
                    }
                }
            }
        }
        return "default";
    }

    /**
     * Gets JBOSS_HOME of the container.
     *
     * @param containerName name of the container
     * @return JBOSS_HOME as specified in arquillian.xml or null
     */
    public static String getJbossHome(String containerName) {

//        for (GroupDef groupDef : arquillianDescriptor.getGroups()) {
//            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
//                if (containerDef.getContainerName().equalsIgnoreCase(containerName)) {
//                    if (containerDef.getContainerProperties().containsKey("jbossHome")) {
//                        return containerDef.getContainerProperties().get("jbossHome");
//                    }
//                }
//            }
//        }
        return getContainerInfo(containerName).getJbossHome();
    }

    /**
     * Returns hostname where the server was bound.
     *
     * @param containerName name of the container
     * @return hostname of "localhost" when no specified
     */
    public static String getHostname(String containerName) {
//        for (GroupDef groupDef : arquillianDescriptor.getGroups()) {
//            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
//                if (containerDef.getContainerName().equalsIgnoreCase(containerName)) {
//                    if (containerDef.getContainerProperties().containsKey("bindAddress")) {
//                        return containerDef.getContainerProperties().get("bindAddress");
//                    }
//                }
//            }
//        }
//        for (GroupDef groupDef : arquillianDescriptor.getGroups()) {
//            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
//                if (containerDef.getContainerName().equalsIgnoreCase(containerName)) {
//                    if (containerDef.getContainerProperties().containsKey("managementAddress")) {
//                        return containerDef.getContainerProperties().get("managementAddress");
//                    }
//                }
//            }
//        }

        return getContainerInfo(containerName).getIpAddress();
    }

    /**
     * Waits for the clients to finish. If they do not finish in the specified time out then it fails the test.
     *
     * @param clients clients
     */
    public void waitForClientsToFinish(Clients clients) {
        waitForClientsToFinish(clients, 600000);
    }

    /**
     * Waits for the clients to finish. If they do not finish in the specified time out then it fails the test.
     *
     * @param clients clients
     * @param timeout timeout
     */
    public void waitForClientsToFinish(Clients clients, long timeout) {
        long startTime = System.currentTimeMillis();
        try {
            while (!clients.isFinished()) {
                Thread.sleep(1000);
                if (System.currentTimeMillis() - startTime > timeout) {
                    Map<Thread, StackTraceElement[]> mst = Thread.getAllStackTraces();
                    StringBuilder stacks = new StringBuilder("Stack traces of all threads:");
                    for (Thread t : mst.keySet()) {
                        stacks.append("Stack trace of thread: ").append(t.toString()).append("\n");
                        StackTraceElement[] elements = mst.get(t);
                        for (StackTraceElement e : elements) {
                            stacks.append("---").append(e).append("\n");
                        }
                        stacks.append("---------------------------------------------\n");
                    }
                    log.error(stacks);
                    for (Client c : clients.getConsumers()) {
                        c.interrupt();
                    }
                    for (Client c : clients.getProducers()) {
                        c.interrupt();
                    }
                    Assert.fail("Clients did not stop in : " + timeout + "ms. Failing the test and trying to kill them all. Print all stacktraces:" + stacks);
                }
            }
        } catch (InterruptedException e) {
            log.error("waitForClientsToFinish failed: ", e);
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
    public boolean waitHornetQToAlive(String ipAddress, int port, long timeout) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (!checkThatServerIsReallyUp(ipAddress, port) && System.currentTimeMillis() - startTime < timeout) {
            Thread.sleep(1000);
        }

        if (!checkThatServerIsReallyUp(ipAddress, port)) {
            Assert.fail("Server: " + ipAddress + ":" + port + " did not start again. Time out: " + timeout);
        }
        return checkThatServerIsReallyUp(ipAddress, port);
    }

    /**
     * Returns true if something is listenning on server
     *
     * @param ipAddress ipAddress
     * @param port      port
     */
    protected boolean checkThatServerIsReallyUp(String ipAddress, int port) {
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
     * Return managementPort as defined in arquillian.xml.
     *
     * @param containerName name of the container
     * @return managementPort or rmiPort or 9999 when not set
     */
    public static int getPort(String containerName) {

        for (GroupDef groupDef : arquillianDescriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                if (containerDef.getContainerName().equalsIgnoreCase(containerName)) {
                    if (containerDef.getContainerProperties().containsKey("managementPort")) {
                        return Integer.valueOf(containerDef.getContainerProperties().get("managementPort"));
                    }
                }
            }
        }

        return 9999;
    }

    /**
     * Return username as defined in arquillian.xml.
     *
     * @param containerName name of the container
     * @return username or null if empty
     */
    public static String getUsername(String containerName) {

        String username;

        for (GroupDef groupDef : arquillianDescriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                if (containerDef.getContainerName().equalsIgnoreCase(containerName)) {
                    if (containerDef.getContainerProperties().containsKey("username")) {
                        return containerDef.getContainerProperties().get("username");
                    }
                }
            }
        }

        return null;
    }

    /**
     * Return password as defined in arquillian.xml.
     *
     * @param containerName name of the container
     * @return password or null if empty
     */
    public static String getPassword(String containerName) {

        for (GroupDef groupDef : arquillianDescriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                if (containerDef.getContainerName().equalsIgnoreCase(containerName)) {
                    if (containerDef.getContainerProperties().containsKey("password")) {
                        return containerDef.getContainerProperties().get("password");
                    }
                }
            }
        }

        return null;
    }

    /**
     * Gets current container for test.
     *
     * @return name
     */
    public String getCurrentContainerForTest() {
        return currentContainerForTest;
    }

    /**
     * Set current container for testing.
     *
     * @param currentContainerForTest container
     */
    public void setCurrentContainerForTest(String currentContainerForTest) {
        this.currentContainerForTest = currentContainerForTest;
    }

    @Before
    public void printTestMethodNameStart() {
        // keep this empty - this is just for arquillian extension to print start of the test
    }

    @After
    public void printTestMethodNameStop() {
        // keep this empty - this is just for arquillian extension to print stop of the test
    }

    /**
     * Returns true if the given number of messages is in queue in the given timeout. Otherwise it returns false.
     *
     * @param containerName            name of the container
     * @param queueCoreName            queue name
     * @param expectedNumberOfMessages number of messages
     * @param timeout                  timeout
     * @return Returns true if the given number of messages is in queue in the given timeout. Otherwise it returns false.
     * @throws Exception
     */
    public boolean waitForNumberOfMessagesInQueue(String containerName, String queueCoreName, int expectedNumberOfMessages, long timeout) throws Exception {

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        long startTime = System.currentTimeMillis();

        while ((jmsAdminOperations.getCountOfMessagesOnQueue(queueCoreName)) < expectedNumberOfMessages &&
                System.currentTimeMillis() - startTime < timeout) {
            Thread.sleep(500);
        }
        jmsAdminOperations.close();

        if (System.currentTimeMillis() - startTime > timeout) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Waits until all containers in the given queue contains the given number of messages
     *
     * @param queueName        queue name
     * @param numberOfMessages number of messages
     * @param timeout          time out
     * @param containerNames   container name
     * @return returns true if there is numberOfMessages in queue, when timeout expires it returns false
     * @throws Exception
     */
    public boolean waitForMessages(String queueName, long numberOfMessages, long timeout, String... containerNames) throws Exception {

        long startTime = System.currentTimeMillis();

        long count = 0;
        while ((count = countMessages(queueName, containerNames)) < numberOfMessages) {
            log.info("Total number of messages in queue: " + queueName + " on node " + containerNames + " is " + count);
            Thread.sleep(1000);
            if (System.currentTimeMillis() - startTime > timeout) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks whether file contains given string.
     *
     * @param fileToCheck
     * @return true if file contains the string
     * @throws Exception
     */
    public boolean checkThatFileContainsUnfinishedTransactionsString(File fileToCheck, String stringToFind) throws Exception {

        Scanner scanner = new Scanner(fileToCheck);

        //now read the file line by line...
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.contains(stringToFind)) {
                return true;
            }
        }
        return false;
    }

    public boolean checkUnfinishedArjunaTransactions(String containerName) {

        JMSOperations jmsOperations = getJMSOperations(containerName);
        boolean unfinishedTransactions2 = jmsOperations.areThereUnfinishedArjunaTransactions();
        jmsOperations.close();

        log.info("Checking arjuna transactions on node: " + containerName + ". Are there unfinished transactions?: " + unfinishedTransactions2);
        return unfinishedTransactions2;

    }

    /**
     * Returns total number of messages in queue on given nodes
     *
     * @param queueName      queue name
     * @param containerNames container name
     * @return total number of messages in queue on given nodes
     */
    public long countMessages(String queueName, String... containerNames) {
        long sum = 0;
        for (String containerName : containerNames) {
            JMSOperations jmsOperations = getJMSOperations(containerName);
            long count = jmsOperations.getCountOfMessagesOnQueue(queueName);
            log.info("Number of messages on node : " + containerName + " is: " + count);
            sum += count;
            jmsOperations.close();
        }
        return sum;
    }

    /**
     * Copies one directory to another.
     *
     * @param srcDir source directory
     * @param dstDir destination directory
     * @throws IOException
     */
    public void copyDirectory(File srcDir, File dstDir) throws IOException {
        log.info("Copy directory: " + srcDir.getAbsolutePath() + " to " + dstDir.getAbsolutePath());
        if (srcDir.isDirectory()) {
            if (!dstDir.exists()) {
                dstDir.mkdir();
            }

            String[] children = srcDir.list();
            for (String aChildren : children) {
                copyDirectory(new File(srcDir, aChildren),
                        new File(dstDir, aChildren));
            }
        } else {
            // This method is implemented in Copying a File
            copyFile(srcDir, dstDir);
        }
    }


    /**
     * Copies file from one place to another.
     *
     * @param sourceFile source file
     * @param destFile   destination file - file will be rewritten
     * @throws IOException
     */
    public void copyFile(File sourceFile, File destFile) throws IOException {
        if (!destFile.exists()) {
            destFile.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;

        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        } finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }
        }
    }

    public String getEapVersion(String containerName) throws Exception {

        File versionFile = new File(getJbossHome(containerName) + File.separator + "version.txt");

        Scanner scanner = new Scanner(new FileInputStream(versionFile));
        String eapVersion = scanner.nextLine();
        log.info("Print content of version file: " + eapVersion);

        String pattern = "(?i)((Red Hat )?JBoss Enterprise Application Platform - Version )(.+?)(.[a-zA-Z]+[0-9]*)";
        String justVersion = eapVersion.replaceAll(pattern, "$3").trim();

        StringTokenizer str = new StringTokenizer(justVersion, ".");
        String majorVersion = str.nextToken();
        String minorVersion = str.nextToken();
        String microVersion;

        switch (Integer.valueOf(majorVersion)) {
            case 5:
                microVersion = str.nextToken();
                break;
            case 6:
                switch (Integer.valueOf(minorVersion)) {
                    case 0:
                        microVersion = str.nextToken();
                        break;
                    case 1:
                        microVersion = str.nextToken();
                        if (Integer.valueOf(microVersion) > 1) {
                            // for 6.1.2+, use driver for version 6.1.1
                            microVersion = "1";
                        }
                        break;
                    default:
                        // for version 6.2.0+ always use driver for '0' micro version
                        microVersion = "0";
                        break;
                }
                break;
            default:
                throw new IllegalArgumentException(
                        "Given container is not EAP5 or EAP6! It says its major version is " + majorVersion);
        }

        return majorVersion + "." + minorVersion + "." + microVersion;
    }

    /**
     * Download jdbc driver from svn jdbc repository to deployments directory.
     *
     * @param eapVersion 6.2.0
     * @param database   oracle12c,mssql2012
     * @throws Exception if anything goes wrong
     */
    public static String donwloadJdbcDriver(String eapVersion, String database) throws Exception {

        URL metaInfUrl = new URL(URL_JDBC_DRIVERS.concat("/" + eapVersion + "/" + database + "/jdbc4//meta-inf.txt"));

        log.info("Print mete-inf url: " + metaInfUrl);

        ReadableByteChannel rbc = Channels.newChannel(metaInfUrl.openStream());
        File targetDirDeployments = new File(getJbossHome(CONTAINER1) + File.separator + "standalone" + File.separator
                + "deployments" + File.separator + "meta-inf.txt");

        FileOutputStream fos = new FileOutputStream(targetDirDeployments);
        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);


        Scanner scanner = new Scanner(new FileInputStream(targetDirDeployments));
        String jdbcFileName = scanner.nextLine();
        log.info("Print jdbc file name: " + jdbcFileName);

        URL jdbcUrl = new URL(URL_JDBC_DRIVERS.concat("/" + eapVersion + "/" + database + "/jdbc4/" + jdbcFileName));
        ReadableByteChannel rbc2 = Channels.newChannel(jdbcUrl.openStream());
        File targetDirDeploymentsForJdbc = new File(getJbossHome(CONTAINER1) + File.separator + "standalone" + File.separator
                + "deployments" + File.separator + jdbcFileName);
        FileOutputStream fos2 = new FileOutputStream(targetDirDeploymentsForJdbc);
        fos2.getChannel().transferFrom(rbc2, 0, Long.MAX_VALUE);

        fos.close();
        fos2.close();
        rbc.close();
        rbc2.close();

        return jdbcFileName;

    }

    /**
     * Wait for given time-out for no xa transactions in prepared state.
     *
     * @param timeout
     * @param containerName
     * @throws Exception
     */
    public void waitUntilThereAreNoPreparedHornetQTransactions(long timeout, String containerName) throws Exception {

        // check that number of prepared transaction gets to 0
        log.info("Get information about transactions from HQ:");

        long startTime = System.currentTimeMillis();

        int numberOfPreparedTransaction = 100;

        JMSOperations jmsOperations = getJMSOperations(containerName);

        while (numberOfPreparedTransaction > 0 && System.currentTimeMillis() - startTime < timeout) {

            numberOfPreparedTransaction = jmsOperations.getNumberOfPreparedTransaction();

            Thread.sleep(1000);

        }

        jmsOperations.close();

        if (System.currentTimeMillis() - startTime < timeout)   {
            log.error("There are prepared transactions in HornetQ journal.");
            Assert.fail("There are prepared transactions in HornetQ journal - number of prepared transactions is: " + numberOfPreparedTransaction);
        }
    }


}
