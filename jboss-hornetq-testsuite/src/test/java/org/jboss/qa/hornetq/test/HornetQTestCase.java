package org.jboss.qa.hornetq.test;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.test.spi.event.suite.BeforeSuite;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.servlets.KillerServlet;
import org.jboss.qa.tools.HornetQAdminOperationsEAP5;
import org.jboss.qa.tools.HornetQAdminOperationsEAP6;
import org.jboss.qa.tools.JBMAdminOperationsEAP5;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Before;

import javax.naming.Context;
import javax.naming.NamingException;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Parent class for all HornetQ test cases. Provides an abstraction of used container
 *
 * @author pslavice@redhat.com
 * @author mnovak@redhat.com
 */
public class HornetQTestCase implements ContextProvider, HornetQTestCaseConstants {

    // Logger
    private static final Logger log = Logger.getLogger(HornetQTestCase.class);

    // JBOSS_HOME properties
    public static String JBOSS_HOME_1;
    public static String JBOSS_HOME_2;
    public static String JBOSS_HOME_3;
    public static String JBOSS_HOME_4;

    // IP address for containers
    public static String CONTAINER1_IP;
    public static String CONTAINER2_IP;
    public static String CONTAINER3_IP;
    public static String CONTAINER4_IP;

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
    private String currentContainerForTest;

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
        CONTAINER1_IP = getEnvProperty("MYTESTIP_1");
        CONTAINER2_IP = getEnvProperty("MYTESTIP_2");
        CONTAINER3_IP = getEnvProperty("MYTESTIP_3");
        CONTAINER4_IP = getEnvProperty("MYTESTIP_4");
        String tmpMultiCastAddress = System.getProperty("MCAST_ADDR");
        MCAST_ADDRESS = tmpMultiCastAddress != null ? tmpMultiCastAddress : "233.3.3.3";

        JBOSS_HOME_1 = verifyJbossHome(getEnvProperty("JBOSS_HOME_1"));
        JBOSS_HOME_2 = verifyJbossHome(getEnvProperty("JBOSS_HOME_2"));
        JBOSS_HOME_3 = verifyJbossHome(getEnvProperty("JBOSS_HOME_3"));
        JBOSS_HOME_4 = verifyJbossHome(getEnvProperty("JBOSS_HOME_4"));

    }

    /**
     * Stops all servers
     */
    @Before
    @After
    public void stopAllServers() {
        stopServer(CONTAINER1);
        stopServer(CONTAINER2);
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
            log.info(String.format("Setting %s='%s'", name, envProperty));
        }
        return envProperty;
    }

    protected Context getContext(String hostName) throws NamingException {
        return getContext(hostName, 4447);
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
     * @see org.jboss.qa.hornetq.test.ContextProvider#getContext()
     */
    public Context getContext() throws NamingException {
        return getContext(CONTAINER1_IP, getJNDIPort());
    }

    /**
     * @see org.jboss.qa.hornetq.test.ContextProvider#getContextContainer1()
     */
    public Context getContextContainer1() throws NamingException {
        return getContext(CONTAINER1_IP, getJNDIPort());
    }

    /**
     * @see org.jboss.qa.hornetq.test.ContextProvider#getContextContainer2()
     */
    public Context getContextContainer2() throws NamingException {
        return getContext(CONTAINER2_IP, getJNDIPort());
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
        if (isEAP5()) {
            HornetQAdminOperationsEAP5 eap5AdmOps = new HornetQAdminOperationsEAP5();
            eap5AdmOps.setHostname(getHostname(container));
            eap5AdmOps.setProfile(getProfile(container));
            eap5AdmOps.setRmiPort(getJNDIPort());
            eap5AdmOps.setJbossHome(getJbossHome(container));
            operations = eap5AdmOps;
        } else if (isEAP6()) {
            HornetQAdminOperationsEAP6 eap6AdmOps = new HornetQAdminOperationsEAP6();
            eap6AdmOps.setHostname(getHostname(container));
            eap6AdmOps.setPort(getPort(container));
            eap6AdmOps.connect();
            operations = eap6AdmOps;
        } else if (isEAP5WithJBM()) {
            JBMAdminOperationsEAP5 eap5AdmOps = new JBMAdminOperationsEAP5();
            eap5AdmOps.setHostname(getHostname(container));
            eap5AdmOps.setProfile(getProfile(container));
            eap5AdmOps.setRmiPort(getJNDIPort());
            eap5AdmOps.setJbossHome(getJbossHome(container));
            operations = eap5AdmOps;
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
    protected boolean deleteFolder(File path) {
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
                killServer(CONTAINER1, SERVLET_KILLER_1, CONTAINER1_IP);
            } else if (CONTAINER2.equals(ContainerName)) {
                killServer(CONTAINER2, SERVLET_KILLER_2, CONTAINER2_IP);
            } else if (CONTAINER3.equals(ContainerName)) {
                killServer(CONTAINER3, SERVLET_KILLER_3, CONTAINER3_IP);
            } else if (CONTAINER4.equals(ContainerName)) {
                killServer(CONTAINER4, SERVLET_KILLER_4, CONTAINER4_IP);
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
        HttpRequest.get("http://" + serverIP + ":8080/KillerServlet/KillerServlet?op=kill", 4, TimeUnit.SECONDS);
        Thread.sleep(3000);
        controller.kill(container);
    }

    /**
     * This is wrapper method for controller.stop() method. Problem is that in EAP 5 this method throws exception
     * when server is not running. We'll swallow this exception.
     *
     * @param containerName name of the container
     */
    public void stopServer(final String containerName) {
        // because of stupid hanging during shutdown in various tests - mdb failover + hq core bridge failover
        // we kill server when it takes too long

        final long pid = getProcessId(containerName);

        Thread shutdownHook = new Thread() {
            public void run() {
                long timeout = 120000;
                long startTime = System.currentTimeMillis();
                while (checkThatServerIsReallyUp(getHostname(containerName), 9999)
                        || checkThatServerIsReallyUp(getHostname(containerName), 5445)
                        || checkThatServerIsReallyUp(getHostname(containerName), 8080)) {

                    if (System.currentTimeMillis() - startTime > timeout) {
                        // kill server because shutdown hangs and fail test
                        try {
                            if (System.getProperty("os.name").contains("Windows"))  {
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
            }
        };
        shutdownHook.start();
        controller.stop(containerName);
    }

    /**
     * Returns -1 when server is not up. Port 8080 is not up.
     *
     * @param containerName container name
     * @return pid of the server
     */
    public long getProcessId(String containerName) {

        if (!checkThatServerIsReallyUp(getHostname(containerName), 8080)) {
            return -1;
        }

        if (CONTAINER1.equals(containerName)) {
            deployer.deploy(SERVLET_KILLER_1);
        } else if (CONTAINER2.equals(containerName)) {
            deployer.deploy(SERVLET_KILLER_2);
        } else if (CONTAINER3.equals(containerName)) {
            deployer.deploy(SERVLET_KILLER_3);
        } else if (CONTAINER4.equals(containerName)) {
            deployer.deploy(SERVLET_KILLER_4);
        } else {
            throw new RuntimeException(String.format("Name of the container %s for is not known. It can't be used", containerName));
        }

        String pid = "";
        try {
            pid = HttpRequest.get("http://" + getHostname(containerName) + ":8080/KillerServlet/KillerServlet?op=getId", 4, TimeUnit.SECONDS);
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

        killerServlet.addAsWebInfResource(new StringAsset(webXml.toString()), "web.xml");
        killerServlet.addAsWebInfResource(new StringAsset(jbossWebXml.toString()), "jboss-web.xml");
        killerServlet.addClass(KillerServlet.class);
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
            }
        }
        return containerId;
    }

    /**
     * Method blocks until all receivers gets the numberOfMessages or timeout expires
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

    /**
     * Gets name of the profile. Related only to EAP 5.
     *
     * @param containerName name of the container
     * @return Name of the profile as specified in arquillian.xml for
     *         profileName or "default" if not set.
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
//        long startTime = System.currentTimeMillis();
//        while(arquillianDescriptor == null && (System.currentTimeMillis() - startTime < 60000))    {
//            log.error("Arquillian descriptor is NULL. Wait...");
//            try {
//                Thread.sleep(5000);
//            } catch (InterruptedException e) {
//                //ignore
//            }
//        }
        for (GroupDef groupDef : arquillianDescriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                if (containerDef.getContainerName().equalsIgnoreCase(containerName)) {
                    if (containerDef.getContainerProperties().containsKey("jbossHome")) {
                        return containerDef.getContainerProperties().get("jbossHome");
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns hostname where the server was bound.
     *
     * @param containerName name of the container
     * @return hostname of "localhost" when no specified
     */
    public static String getHostname(String containerName) {
        for (GroupDef groupDef : arquillianDescriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                if (containerDef.getContainerName().equalsIgnoreCase(containerName)) {
                    if (containerDef.getContainerProperties().containsKey("bindAddress")) {
                        return containerDef.getContainerProperties().get("bindAddress");
                    }
                }
            }
        }
        for (GroupDef groupDef : arquillianDescriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                if (containerDef.getContainerName().equalsIgnoreCase(containerName)) {
                    if (containerDef.getContainerProperties().containsKey("managementAddress")) {
                        return containerDef.getContainerProperties().get("managementAddress");
                    }
                }
            }
        }
        return "localhost";
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
    public void waitForClientsToFinish(Clients clients, long timeout)  {
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
    boolean checkThatServerIsReallyUp(String ipAddress, int port) {
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

}
