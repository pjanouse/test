package org.jboss.qa.hornetq;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.test.spi.event.suite.BeforeSuite;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.interceptors.LargeMessagePacketInterceptor;
import org.jboss.qa.hornetq.apps.jmx.JmxNotificationListener;
import org.jboss.qa.hornetq.apps.jmx.JmxUtils;
import org.jboss.qa.hornetq.junit.rules.ArchiveServerLogsAfterFailedTest;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.ContainerInfo;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.JdbcUtils;
import org.jboss.qa.hornetq.tools.journal.JournalExportImportUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.naming.Context;
import javax.naming.NamingException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.*;

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
    @Deprecated
    public final static String JBOSS_HOME_1;
    @Deprecated
    public final static String JBOSS_HOME_2;
    @Deprecated
    public final static String JBOSS_HOME_3;
    @Deprecated
    public final static String JBOSS_HOME_4;

    // IP address for containers
    public final static String DEFAULT_CONTAINER_IP = "127.0.0.1";
    @Deprecated
    public final static String CONTAINER1_IP;
    @Deprecated
    public final static String CONTAINER2_IP;
    @Deprecated
    public final static String CONTAINER3_IP;
    @Deprecated
    public final static String CONTAINER4_IP;

    // Multi-cast address
    @Deprecated
    /**
     * @deprecated use @Container.MCAST_ADDRESS instead
     */
    public static final String MCAST_ADDRESS;

    // Journal directory for first live/backup pair or first node in cluster
    public static final String JOURNAL_DIRECTORY_A;

    // Journal directory for second live/backup pair or second node in cluster
    public static final String JOURNAL_DIRECTORY_B;

    // Active server - EAP 5 or EAP 6?
    @Deprecated
    protected String currentContainerForTest;

    @ArquillianResource
    private ContainerController controller;

    @ArquillianResource
    @Deprecated
    /**
     * @deprecated use @Container deploy/undeploy methods instead
     */
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
        String tmpIpAddress = getEnvProperty("MYTESTIP_1");
        CONTAINER1_IP = checkIPv6Address(tmpIpAddress == null ? DEFAULT_CONTAINER_IP : tmpIpAddress);
        tmpIpAddress = getEnvProperty("MYTESTIP_2");
        CONTAINER2_IP = checkIPv6Address(tmpIpAddress== null ? DEFAULT_CONTAINER_IP : tmpIpAddress);
        tmpIpAddress = getEnvProperty("MYTESTIP_3");
        CONTAINER3_IP = checkIPv6Address(tmpIpAddress== null ? DEFAULT_CONTAINER_IP : tmpIpAddress);
        tmpIpAddress = getEnvProperty("MYTESTIP_4");
        CONTAINER4_IP = checkIPv6Address(tmpIpAddress== null ? DEFAULT_CONTAINER_IP : tmpIpAddress);

        // if MCAST_ADDR is null then generate multicast address
        String tmpMultiCastAddress = getEnvProperty("MCAST_ADDR");
        tmpMultiCastAddress = tmpMultiCastAddress != null ? tmpMultiCastAddress :
                new StringBuilder().append(randInt(224, 239)).append(".").append(randInt(1, 254)).append(".")
                        .append(randInt(1, 254)).append(".").append(randInt(1, 254)).toString();
        MCAST_ADDRESS = checkMulticastAddress(tmpMultiCastAddress);

        JBOSS_HOME_1 = verifyJbossHome(getEnvProperty("JBOSS_HOME_1"));
        JBOSS_HOME_2 = verifyJbossHome(getEnvProperty("JBOSS_HOME_2"));
        JBOSS_HOME_3 = verifyJbossHome(getEnvProperty("JBOSS_HOME_3"));
        JBOSS_HOME_4 = verifyJbossHome(getEnvProperty("JBOSS_HOME_4"));

    }

    @Deprecated
    private static String checkMulticastAddress(String multiCastAddress) {
        if (multiCastAddress == null) {
            log.error("Environment variable for MCAST_ADDR is empty (see above), please setup correct MCAST_ADDR variable." +
                    " Stopping test suite by throwing runtime exception.");
            throw new RuntimeException("MCAST_ADDR is null, please setup correct MCAST_ADDR");
        }

        InetAddress ia = null;

        try {
            ia = InetAddress.getByName(multiCastAddress);
            if (!ia.isMulticastAddress())   {
                log.error("Address: " + multiCastAddress + " cannot be found. Double check your MCAST_ADDR environment variable.");
                throw new RuntimeException("Address: " + multiCastAddress + " cannot be found. Double check your  properties whether they're correct.");
            }
        } catch (UnknownHostException e) {
            log.error("Address: " + multiCastAddress + " cannot be found. Double check your properties whether they're correct.", e);
        }
        return multiCastAddress;
    }

    // composite info objects for easier passing to utility classes
    @Deprecated
    public static final ContainerInfo CONTAINER1_INFO = new ContainerInfo(CONTAINER1_NAME, "server-1", CONTAINER1_IP,
            BYTEMAN_CONTAINER1_PORT, 0, JBOSS_HOME_1);
    @Deprecated
    public static final ContainerInfo CONTAINER2_INFO = new ContainerInfo(CONTAINER2_NAME, "server-2", CONTAINER2_IP,
            BYTEMAN_CONTAINER2_PORT, 0, JBOSS_HOME_2);
    @Deprecated
    public static final ContainerInfo CONTAINER3_INFO = new ContainerInfo(CONTAINER3_NAME, "server-3", CONTAINER3_IP,
            BYTEMAN_CONTAINER3_PORT, 0, JBOSS_HOME_3);
    @Deprecated
    public static final ContainerInfo CONTAINER4_INFO = new ContainerInfo(CONTAINER4_NAME, "server-4", CONTAINER4_IP,
            BYTEMAN_CONTAINER4_PORT, 0, JBOSS_HOME_4);
    /////////////////////////////////////////////////////////////////////////////
    // Assign container dependent tooling ///////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////
    private final Map<Integer, org.jboss.qa.hornetq.Container> containers = new HashMap<Integer, org.jboss.qa.hornetq.Container>();
//    public static final ContainerUtils containerUtils = getContainerUtils();
    public static final JmxUtils jmxUtils = getJmxUtils();
//    public static final JmxNotificationListener jmxNotificationListener = getJmxNotificationListener();

    public org.jboss.qa.hornetq.Container container(int index) {
        if (!containers.containsKey(index)) {
            containers.put(index, createContainer("node-" + index, index));
        }

        org.jboss.qa.hornetq.Container container = containers.get(index);

        container.update(controller, deployer);

        return container;
    }

    public Collection<org.jboss.qa.hornetq.Container> containerList() {
        return Collections.unmodifiableCollection(containers.values());
    }

    private org.jboss.qa.hornetq.Container createContainer(String name, int index) {
        ServiceLoader<org.jboss.qa.hornetq.Container> loader = ServiceLoader.load(org.jboss.qa.hornetq.Container.class);
        Iterator<org.jboss.qa.hornetq.Container> iterator = loader.iterator();
        if (!iterator.hasNext()) {
            throw new RuntimeException("No implementation found for Container");
        }

        org.jboss.qa.hornetq.Container c = iterator.next();
        c.init(name, index, getArquillianDescriptor(), controller);
        return c;
    }

    /**
     * Initializes ContainerUtils instance at 1st call. We do not allow any later modifications of ContainerUtils instance
     *  already created.
     *
     * @return container utils instance
     */
/*    private static synchronized ContainerUtils getContainerUtils()   {

        if (containerUtils != null) {
            return containerUtils;
        }

        ServiceLoader serviceLoader = ServiceLoader.load(ContainerUtils.class);

        Iterator<ContainerUtils> iterator = serviceLoader.iterator();

        if (!iterator.hasNext()) {
            throw new RuntimeException("No implementation found for ContainerUtils.");
        }

        ContainerUtils cUtils = iterator.next();

        return cUtils.getInstance(getArquillianDescriptor(), CONTAINER1_INFO, CONTAINER2_INFO, CONTAINER3_INFO, CONTAINER4_INFO);
    }*/

    /**
     * Initializes JmxUtils. Each call will create new instance.
     *
     * @return jmx utils instance
     *
     * @deprecated Use {@link Container#getJmxUtils()}
     */
    @Deprecated
    private static synchronized JmxUtils getJmxUtils()   {

        ServiceLoader<JmxUtils> serviceLoader = ServiceLoader.load(JmxUtils.class);
        Iterator<JmxUtils> iterator = serviceLoader.iterator();

        if (!iterator.hasNext()) {
            throw new RuntimeException("No implementation found for JmxUtils.");
        }

        JmxUtils jmxUtils = iterator.next();

        return jmxUtils;
    }

    /**
     * Initializes JmxNotificationListener instance at 1st call. Creates new instance for every call.
     *
     * @return JmxNotificationListener instance
     *
     * @deprecated Use {@link Container#createJmxNotificationListener()}
     */
    @Deprecated
    protected static synchronized JmxNotificationListener getJmxNotificationListener()    {

        ServiceLoader<JmxNotificationListener> serviceLoader = ServiceLoader.load(JmxNotificationListener.class);

        Iterator<JmxNotificationListener> iterator = serviceLoader.iterator();

        if (!iterator.hasNext()) {
            throw new RuntimeException("No implementation found for JmxUtils.");
        }

        JmxNotificationListener jmxNotificationListener = iterator.next();

        return jmxNotificationListener;
    }

    /**
     * Stops all servers
     */
    @After
    public void stopAllServers() {
        for (org.jboss.qa.hornetq.Container c : containerList()) {
            c.stop();
        }
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
    @Deprecated
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
     * @param jbossHome               jboss home
     */
    @Deprecated
    private static String verifyJbossHome(String jbossHome) {

        if (jbossHome == null) {
            log.error("Environment variable for JBOSS_HOME_X is empty (see above), please setup correct JBOSS_HOME_ variable." +
                    " Stopping test suite by throwing runtime exception.");
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
    @Deprecated
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
    @Deprecated
    private static String getEnvProperty(String name) {

        String envProperty = System.getProperty(name);

        log.info("export " + name + "=" + envProperty);

        return envProperty;
    }

    /**
     * @deprecated Use {@link Container#getContext()}
     */
    @Deprecated
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
     *
     * @deprecated Use {@link Container#getContext()}
     */
    @Deprecated
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
     *
     * @deprecated Use {@link Container#getContext()}
     */
    @Deprecated
    protected Context getEAP6Context(String hostName, int port) throws NamingException {
        return JMSTools.getEAP6Context(hostName, port);
    }

    /**
     * Returns EAP 5 context
     *
     * @param hostName host name
     * @param port     target port with the service
     * @return instance of the context
     * @throws NamingException if something goes wrong
     *
     * @deprecated Use {@link Container#getContext()}
     */
    @Deprecated
    protected Context getEAP5Context(String hostName, int port) throws NamingException {
        return JMSTools.getEAP5Context(hostName, port);
    }

    /**
     * Returns port for JNDI service
     *
     * @return returns default port for JNDI service
     *
     * @deprecated Use {@link Container#getJNDIPort()}
     */
    @Deprecated
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
     *
     * @deprecated Use {@link Container#getJNDIPort()}
     */
    @Deprecated
    public static int getJNDIPort(String containerName) {

        if (getContainerInfo(containerName).getContainerType() == CONTAINER_TYPE.EAP5_CONTAINER
                || getContainerInfo(containerName).getContainerType() == CONTAINER_TYPE.EAP5_WITH_JBM_CONTAINER
                || getContainerInfo(containerName).getContainerType() == CONTAINER_TYPE.EAP6_LEGACY_CONTAINER) {
            return 1099 + getContainerInfo(containerName).getPortOffset();
        } else {
            return 4447 + getContainerInfo(containerName).getPortOffset();
        }
    }

    @Deprecated
    public static int getLegacyJNDIPort(String containerName) {

        return 1099 + getContainerInfo(containerName).getPortOffset();

    }

    /**
     * @see org.jboss.qa.hornetq.ContextProvider#getContext()
     *
     * @deprecated Use {@link Container#getContext()}
     */
    @Deprecated
    public Context getContext() throws NamingException {
        return getContext(container(1).getHostname(), container(1).getJNDIPort());
    }

    /**
     * @see org.jboss.qa.hornetq.ContextProvider#getContextContainer1()
     *
     * @deprecated Use {@link Container#getContext()}
     */
    @Deprecated
    public Context getContextContainer1() throws NamingException {
        return getContext(container(1).getHostname(), container(1).getJNDIPort());
    }

    /**
     * @see org.jboss.qa.hornetq.ContextProvider#getContextContainer2()
     *
     * @deprecated Use {@link Container#getContext()}
     */
    @Deprecated
    public Context getContextContainer2() throws NamingException {
        return getContext(container(2).getHostname(), container(2).getJNDIPort());
    }

    /**
     * Returns JNDI name for the connection factory
     *
     * @return JNDI name for CNF
     */
    @Deprecated
    /**
     * @deprecated replaced by @Container getConnectionFactoryName
     */
    public String getConnectionFactoryName() {
        return (isEAP5()) ? CONNECTION_FACTORY_JNDI_EAP5 : CONNECTION_FACTORY_JNDI_EAP6;
    }

    /**
     * Return true if container is EAP 5 with JBM.
     *
     * @return true if EAP 5 with JBM
     */
    @Deprecated
    private boolean isEAP5WithJBM() {
        return (this.getCurrentContainerForTest() != null) && EAP5_WITH_JBM_CONTAINER.equals(this.getCurrentContainerForTest());
    }

    /**
     * Deletes given folder and all sub folders
     *
     * @param path folder which should be deleted
     * @return true if operation was successful, false otherwise
     *
     * @deprecated Use {@link FileUtils#deleteDirectory(File)} instead.
     */
    @Deprecated
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
     *
     * @deprecated Use {@link org.jboss.qa.hornetq.Container#deleteDataFolder()} instead.
     */
    @Deprecated
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
     *
     * @deprecated Use {@link org.jboss.qa.hornetq.Container#deleteDataFolder()} instead.
     */
    @Deprecated
    protected boolean deleteDataFolderForJBoss1() {
        return deleteDataFolder(JBOSS_HOME_1, CONTAINER1_NAME);
    }

    /**
     * Deletes data folder for given JBoss home - system property JBOSS_HOME_2,
     * removes standalone data folder for standalone profile.
     *
     * @return true if operation was successful, false otherwise
     *
     * @deprecated Use {@link org.jboss.qa.hornetq.Container#deleteDataFolder()} instead.
     */
    @Deprecated
    protected boolean deleteDataFolderForJBoss2() {
        return deleteDataFolder(JBOSS_HOME_2, CONTAINER2_NAME);
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
        log.info("Stop test -------------------------------- " + event.getTestClass().getName() + "." + event
                .getTestMethod().getName());
    }

    /**
     * Returns 8080 + portOffset.
     * <p/>
     * Port offset is specified in pom.xml - PORT_OFFSET_X property.
     *
     * @param container
     * @return 8080 + portOffset
     *
     * @deprecated Use {@link Container#getHttpPort()}
     */
    @Deprecated
    public static int getHttpPort(String container) {
        return 8080 + getContainerInfo(container).getPortOffset();
    }

    /**
     * Returns container info for given container.
     *
     * @param containerName name of the container
     * @return container info class
     */
    @Deprecated
    public static ContainerInfo getContainerInfo(String containerName) {
        if (CONTAINER1_NAME.equals(containerName)) {
            return CONTAINER1_INFO;
        } else if (CONTAINER2_NAME.equals(containerName)) {
            return CONTAINER2_INFO;
        } else if (CONTAINER3_NAME.equals(containerName)) {
            return CONTAINER3_INFO;
        } else if (CONTAINER4_NAME.equals(containerName)) {
            return CONTAINER4_INFO;
        } else {
            throw new RuntimeException(
                    String.format("Name of the container %s is not known. It can't be used", containerName));
        }
    }

    /**
     * @deprecated Use {@link Container#getBytemanPort()}
     */
    @Deprecated
    public int getBytemanPort(String containerName) throws Exception {

        return getContainerInfo(containerName).getBytemanPort();

    }

    /**
     * @deprecated Use {@link Container#getHornetqPort()}
     */
    @Deprecated
    public static int getHornetqPort(String containerName) {
        return PORT_HORNETQ_DEFAULT_EAP6 + getContainerInfo(containerName).getPortOffset();
    }

    /**
     * This port for collocated backup.
     *
     * @param containerName name of the container
     * @return port of collocated backup
     *
     * @deprecated Use {@link Container#getHornetqBackupPort()}
     */
    @Deprecated
    public static int getHornetqBackupPort(String containerName) {
        return PORT_HORNETQ_BACKUP_DEFAULT_EAP6 + getContainerInfo(containerName).getPortOffset();
    }


    /**
     * Returns -1 when server is not up. Port 8080 is not up.
     *
     * @param containerName container name
     * @return pid of the server
     */
    @Deprecated
    public long getProcessId(String containerName) {

        long pid = -1;

        if (!CheckServerAvailableUtils.checkThatServerIsReallyUp(getHostname(containerName), getHttpPort(containerName))) {
            return pid;
        }

        try {
            JMXConnector jmxConnector = jmxUtils.getJmxConnectorForEap(container(1).getHostname(), container(1).getPort());

            MBeanServerConnection mbsc =
                    jmxConnector.getMBeanServerConnection();
            ObjectName oname = new ObjectName(ManagementFactory.RUNTIME_MXBEAN_NAME);

            // form process_id@hostname (f.e. 1234@localhost)
            String runtimeName = (String) mbsc.getAttribute(oname, "Name");

           pid = Long.valueOf(runtimeName.substring(0, runtimeName.indexOf("@")));
        } catch (Exception ex)  {
            log.error("Getting process id failed: ", ex);
        }

        return pid;
    }

    /**
     * Returns true if current container is EAP 5
     *
     * @return true for EAP 5 container
     */
    @Deprecated
    public boolean isEAP5() {
        return ((this.getCurrentContainerForTest() != null) && EAP5_CONTAINER.equals(this.getCurrentContainerForTest()));
    }

    /**
     * Returns true if current container is EAP 5
     *
     * @return true for EAP 6 container
     */
    @Deprecated
    public boolean isEAP6() {
        return (this.getCurrentContainerForTest() != null) && EAP6_CONTAINER.equals(this.getCurrentContainerForTest());
    }

    /**
     * Methods determines current container (EAP 6 or EAP 5)
     *
     * @return container id
     *
     * @deprecated Use {@link Container#getContainerType()}
     */
    @Deprecated
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
            } else if (arqConfigurationFile.toLowerCase().contains("domain")) {
                containerId = EAP6_DOMAIN_CONTAINER;
            }
        }
        return containerId;
    }

    /**
     * Method blocks until all receivers gets the numberOfMessages or timeout expires
     * <p/>
     * This is NOT sum{receivers.getCount()}. Each receiver must have numberOfMessages.
     *
     * @deprecated replaced by @link ClientUtils waitForReceiversUntil()
     * @param receivers        receivers
     * @param numberOfMessages numberOfMessages
     * @param timeout          timeout
     */
    @Deprecated
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
     * Method blocks until sum of messages received is equal or greater the numberOfMessages, if timeout expires then Assert.fail
     * <p/>
     *
     * @param receivers        receivers
     * @param numberOfMessages numberOfMessages
     * @param timeout          timeout
     */
    @Deprecated
    public void waitForAtLeastOneReceiverToConsumeNumberOfMessages(List<Client> receivers, int numberOfMessages, long timeout) {
        long startTimeInMillis = System.currentTimeMillis();

        int sum = 0;

        do {

            sum = 0;

            for (Client c : receivers) {
                sum += c.getCount();
            }

            if ((System.currentTimeMillis() - startTimeInMillis) > timeout) {
                Assert.fail("Clients did not receive " + numberOfMessages + " in timeout: " + timeout);
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        } while (sum <= numberOfMessages);
    }


    /**
     * @deprecated use @see ClientUtils#waitForProducersUntil()
     * Method blocks until all receivers gets the numberOfMessages or timeout expires
     *
     * @param producers        receivers
     * @param numberOfMessages numberOfMessages
     * @param timeout          timeout
     */
    @Deprecated
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

    @Deprecated
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
    @Deprecated
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
     *
     * @deprecated Use {@link Container#getServerHome()}
     */
    @Deprecated
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
     *
     * @deprecated Use {@link Container#getHostname()}
     */
    @Deprecated
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
     * Waits for the org.jboss.qa.hornetq.apps.clients to finish. If they do not finish in the specified time out then it fails the test.
     *
     * @param clients org.jboss.qa.hornetq.apps.clients
     */
    @Deprecated
    public void waitForClientsToFinish(Clients clients) {
        waitForClientsToFinish(clients, 600000);
    }

    /**
     * Waits for the org.jboss.qa.hornetq.apps.clients to finish. If they do not finish in the specified time out then it fails the test.
     *
     * @param clients org.jboss.qa.hornetq.apps.clients
     * @param timeout timeout
     */
    @Deprecated
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
     * @deprecated  replaced by @see org.jboss.qa.hornetq.tools.CheckServerAvailableUtils#waitHornetQToAlive()
     *
     * @param ipAddress ipAddress
     * @param port      port
     * @param timeout   timeout
     */
    @Deprecated
    public boolean waitHornetQToAlive(String ipAddress, int port, long timeout) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (!CheckServerAvailableUtils.checkThatServerIsReallyUp(ipAddress, port) && System.currentTimeMillis() - startTime < timeout) {
            Thread.sleep(1000);
        }

        if (!CheckServerAvailableUtils.checkThatServerIsReallyUp(ipAddress, port)) {
            Assert.fail("Server: " + ipAddress + ":" + port + " did not start again. Time out: " + timeout);
        }
        return CheckServerAvailableUtils.checkThatServerIsReallyUp(ipAddress, port);
    }

    /**
     * Return managementPort as defined in arquillian.xml.
     *
     * @param containerName name of the container
     * @return managementPort or rmiPort or 9999 when not set
     *
     * @deprecated Use {@link Container#getPort()}
     */
    @Deprecated
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
     *
     * @deprecated Use {@link Container#getUsername()}
     */
    @Deprecated
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
     *
     * @deprecated {@link Container#getPassword()}
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
     * @deprecated  use @see Container#getContainerType()
     *
     * @return name
     */
    @Deprecated
    public String getCurrentContainerForTest() {
        return currentContainerForTest;
    }

    /**
     * Set current container for testing.
     *
     * @param currentContainerForTest container
     */
    @Deprecated
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
     * @param container                container
     * @param queueCoreName            queue name
     * @param expectedNumberOfMessages number of messages
     * @param timeout                  timeout
     * @return Returns true if the given number of messages is in queue in the given timeout. Otherwise it returns false.
     * @throws Exception
     */
    @Deprecated
    public boolean waitForNumberOfMessagesInQueue(org.jboss.qa.hornetq.Container container, String queueCoreName,
            int expectedNumberOfMessages, long timeout) throws Exception {

        JMSOperations jmsAdminOperations = container.getJmsOperations();

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
     * * @deprecated use @see JMSTools#waitForMessages instead
     *
     * @param queueName        queue name
     * @param numberOfMessages number of messages
     * @param timeout          time out
     * @param containers       container list
     * @return returns true if there is numberOfMessages in queue, when timeout expires it returns false
     * @throws Exception
     */
    @Deprecated
    public boolean waitForMessages(String queueName, long numberOfMessages, long timeout, org.jboss.qa.hornetq.Container... containers) throws Exception {

        long startTime = System.currentTimeMillis();

        long count = 0;
        while ((count = countMessages(queueName, containers)) < numberOfMessages) {
            List<String> containerNames = new ArrayList<String>(containers.length);
            for (org.jboss.qa.hornetq.Container c: containers) {
                containerNames.add(c.getName());
            }

            log.info("Total number of messages in queue: " + queueName + " on node "
                    + Arrays.toString(containerNames.toArray()) + " is " + count);
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
     * @deprecated use @see TransactionUtils#checkThatFileContainsUnfinishedTransactionsString()
     *
     * @param fileToCheck
     * @return true if file contains the string
     * @throws Exception
     */
    @Deprecated
    public boolean checkThatFileContainsUnfinishedTransactionsString(File fileToCheck, String stringToFind) throws Exception {

        return checkThatFileContainsGivenString(fileToCheck, stringToFind);

    }

    /**
     * Checks whether file contains given string.
     *
     * @deprecated use @see TransactionUtils#checkThatFileContainsGivenString()
     *
     * @param fileToCheck
     * @return true if file contains the string, false if not
     * @throws Exception if file does not exist or any other error
     */
    @Deprecated
    public boolean checkThatFileContainsGivenString(File fileToCheck, String stringToFind) throws Exception {
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

    public boolean checkUnfinishedArjunaTransactions(org.jboss.qa.hornetq.Container container) {

        JMSOperations jmsOperations = container.getJmsOperations();
        boolean unfinishedTransactions2 = jmsOperations.areThereUnfinishedArjunaTransactions();
        jmsOperations.close();

        log.info("Checking arjuna transactions on node: " + container.getName()
                + ". Are there unfinished transactions?: " + unfinishedTransactions2);
        return unfinishedTransactions2;

    }

    /**
     * Returns total number of messages in queue on given nodes
     *
     * @deprecated use @see JMSTools#countMessages instead
     *
     * @param queueName      queue name
     * @param containers     container list
     * @return total number of messages in queue on given nodes
     */
    @Deprecated
    public long countMessages(String queueName, org.jboss.qa.hornetq.Container... containers) {
        long sum = 0;
        for (org.jboss.qa.hornetq.Container container : containers) {
            JMSOperations jmsOperations = container.getJmsOperations();

            ContainerInfo ci = getContainerInfo(container.getName());
            if (CONTAINER_TYPE.EAP6_DOMAIN_CONTAINER.equals(ci.getContainerType())) {
                jmsOperations.addAddressPrefix("host", "master");
                jmsOperations.addAddressPrefix("server", ci.getDomainName());
            }
            long count = jmsOperations.getCountOfMessagesOnQueue(queueName);
            log.info("Number of messages on node : " + container + " is: " + count);
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
     *
     * @deprecated Use {@link FileUtils#copyDirectory(File, File)}
     */
    @Deprecated
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
     *
     * @deprecated Use {@link FileUtils#copyFile(File, File)}
     */
    @Deprecated
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

    /**
     * @param containerName
     * @return
     * @throws Exception
     *
     * @deprecated Use {@link Container#getServerVersion()} instead.
     */
    @Deprecated
    /**
     *
     */
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
     *
     * @deprecated Use {@link JdbcUtils#downloadJdbcDriver(Container, String)} instead.
     */
    @Deprecated
    public String donwloadJdbcDriver(String eapVersion, String database) throws Exception {

        URL metaInfUrl = new URL(URL_JDBC_DRIVERS.concat("/" + eapVersion + "/" + database + "/jdbc4//meta-inf.txt"));

        log.info("Print mete-inf url: " + metaInfUrl);

        ReadableByteChannel rbc = Channels.newChannel(metaInfUrl.openStream());
        File targetDirDeployments = new File(container(1).getServerHome() + File.separator + "standalone" + File.separator
                + "deployments" + File.separator + "meta-inf.txt");

        FileOutputStream fos = new FileOutputStream(targetDirDeployments);
        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);


        Scanner scanner = new Scanner(new FileInputStream(targetDirDeployments));
        String jdbcFileName = scanner.nextLine();
        log.info("Print jdbc file name: " + jdbcFileName);

        URL jdbcUrl = new URL(URL_JDBC_DRIVERS.concat("/" + eapVersion + "/" + database + "/jdbc4/" + jdbcFileName));
        ReadableByteChannel rbc2 = Channels.newChannel(jdbcUrl.openStream());
        File targetDirDeploymentsForJdbc = new File(container(1).getServerHome() + File.separator + "standalone" + File.separator
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
     * @deprecated use @see TransactionUtils#waitUntilThereAreNoPreparedHornetQTransactions
     *
     * @param timeout
     * @param container
     * @throws Exception
     */
    @Deprecated
    public void waitUntilThereAreNoPreparedHornetQTransactions(long timeout, org.jboss.qa.hornetq.Container container) throws Exception {

        // check that number of prepared transaction gets to 0
        log.info("Get information about transactions from HQ:");

        long startTime = System.currentTimeMillis();

        int numberOfPreparedTransaction = 100;

        JMSOperations jmsOperations = container.getJmsOperations();

        while (numberOfPreparedTransaction > 0 && System.currentTimeMillis() - startTime < timeout) {

            numberOfPreparedTransaction = jmsOperations.getNumberOfPreparedTransaction();

            Thread.sleep(1000);

        }

        jmsOperations.close();

        if (System.currentTimeMillis() - startTime > timeout) {
            log.error("There are prepared transactions in HornetQ journal.");
            Assert.fail("There are prepared transactions in HornetQ journal - number of prepared transactions is: " + numberOfPreparedTransaction);
        }
    }


}
