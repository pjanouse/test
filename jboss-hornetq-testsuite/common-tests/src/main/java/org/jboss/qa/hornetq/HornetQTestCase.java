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
import org.jboss.qa.PrepareCoordinator;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.jmx.JmxNotificationListener;
import org.jboss.qa.hornetq.apps.jmx.JmxUtils;
import org.jboss.qa.hornetq.junit.rules.ArchiveServerLogsAfterFailedTest;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.ContainerInfo;
import org.jboss.qa.hornetq.tools.DBAllocatorUtils;
import org.jboss.qa.hornetq.tools.DebugTools;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

/**
 * Parent class for all HornetQ test cases. Provides an abstraction of used container
 *
 * @author pslavice@redhat.com
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
public class HornetQTestCase implements HornetQTestCaseConstants {

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

    // Journal directory for server in colocated replicated topology (each server must have own journal)
    public static final String JOURNAL_DIRECTORY_C;

    // Journal directory for server in colocated replicated topology (each server must have own journal)
    public static final String JOURNAL_DIRECTORY_D;

    @ArquillianResource
    protected ContainerController controller;

    @ArquillianResource
    @Deprecated
    /**
     * @deprecated use @Container deploy/undeploy methods instead
     */
    protected Deployer deployer;

    protected List<Client> usedClients = new ArrayList<Client>();

    protected PrepareCoordinator prepareCoordinator = new PrepareCoordinator("org.jboss.qa");

    // this property is initialized during BeforeClass phase by ArquillianConfiguration extension
    private static ArquillianDescriptor arquillianDescriptor;

    static {
        // Path to the journal
        String tmpJournalA = getEnvProperty("JOURNAL_DIRECTORY_A");
        JOURNAL_DIRECTORY_A = (tmpJournalA != null && !"".equals(tmpJournalA)) ? tmpJournalA : "../../../../hornetq-journal-A";
        String tmpJournalB = getEnvProperty("JOURNAL_DIRECTORY_B");
        JOURNAL_DIRECTORY_B = (tmpJournalB != null && !"".equals(tmpJournalB)) ? tmpJournalB : "../../../../hornetq-journal-B";
        String tmpJournalC = getEnvProperty("JOURNAL_DIRECTORY_C");
        JOURNAL_DIRECTORY_C = (tmpJournalC != null && !"".equals(tmpJournalC)) ? tmpJournalC : "../../../../hornetq-journal-C";
        String tmpJournalD = getEnvProperty("JOURNAL_DIRECTORY_D");
        JOURNAL_DIRECTORY_D = (tmpJournalD != null && !"".equals(tmpJournalD)) ? tmpJournalD : "../../../../hornetq-journal-D";

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

    @Rule
    public TestRule watcher = new TestWatcher() {
        @Override
        protected void starting(Description description) {
            prepareCoordinator.clear();
            prepareCoordinator.processTest(description.getTestClass(), description.getMethodName());
            prepareCoordinator.processSystemProperties();

            if (System.getProperty("eap") == null || System.getProperty("eap").startsWith("6x")) {
                prepareCoordinator.requireLabel("EAP6");
            } else if (System.getProperty("eap").startsWith("7x")) {
                prepareCoordinator.requireLabel("EAP7");
            }
        }
    };

    @Before
    public void setUp() {
        prepareCoordinator.getParams().put("container1", container(1));
        prepareCoordinator.getParams().put("container2", container(2));
        prepareCoordinator.getParams().put("container3", container(3));
        prepareCoordinator.getParams().put("container4", container(4));
        try {
            log.info("###############################################################################################");
            log.info("################################   Starting Prepare phase   ###################################");
            log.info("###############################################################################################");
            prepareCoordinator.invokePrepare();
            log.info("###############################################################################################");
            log.info("################################   Ending Prepare phase     ###################################");
            log.info("###############################################################################################");
        } catch (InvocationTargetException e) {
            log.error("ERROR", e.getCause());
            throw new RuntimeException(e);
        }
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

        container.update(controller);

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

    @After
    public void stopAllClients() throws Exception {
        long timeToWait = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < timeToWait) {
            boolean allClientsAreStopped = true;
            for (Client client : usedClients) {
                log.info("" + client + " isAlive=" + client.isAlive());
                if (client.isAlive()) {
                    client.forcedStop();
                    allClientsAreStopped = false;
                }
            }
            if (allClientsAreStopped) {
                break;
            }
            Thread.sleep(1000);
        }
        boolean stopped = true;
        for (Client client : usedClients) {
            log.info("" + client + " isAlive=" + client.isAlive());
            if (client.isAlive()) {
                stopped = false;
                break;
            }
        }
        if (!stopped) {
            DebugTools.printThreadDump();
            Assert.fail("Clients did not stopped in 30 seconds.");
        }
        usedClients.clear();
    }

    @After
    public void deallocateDatabase() throws Exception {
        DBAllocatorUtils.free();
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
     * Returns and logs value of the env property
     *
     * @param name name of
     * @return value of the defined property or null
     */
    private static String getEnvProperty(String name) {

        String envProperty = System.getProperty(name);

        log.info("export " + name + "=" + envProperty);

        return envProperty;
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
     * @deprecated use @see ClientUtils#waitForReceiversUntil
     *
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
     * @deprecated moved to tooling.JMSTools
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
     * @deprecated use @see ClientUtils#waitForClientsToFinish
     * Waits for the org.jboss.qa.hornetq.apps.clients to finish. If they do not finish in the specified time out then it fails the test.
     *
     * @deprecated moved to tooling.JmsTools
     */
    @Deprecated
    public void waitForClientsToFinish(Clients clients) {
        waitForClientsToFinish(clients, 600000);
    }

    /**
     * @deprecated use @see ClientUtils#waitForClientsToFinish
     *
     * Waits for the org.jboss.qa.hornetq.apps.clients to finish. If they do not finish in the specified time out then it fails the test.
     *
     * @param clients org.jboss.qa.hornetq.apps.clients
     * @param timeout timeout
     * @deprecated moved to tooling.JmsTools
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
     * @deprecated moved to JMSTools
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
     * @deprecated use @see JMSTools#waitUntilNumberOfMessagesInQueueIsBelow
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
     * @deprecated use @see CheckFileContentUtils#checkThatFileContainsGivenString()
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

    protected Client addClient(Client client) {
        usedClients.add(client);
        return client;
    }

}
