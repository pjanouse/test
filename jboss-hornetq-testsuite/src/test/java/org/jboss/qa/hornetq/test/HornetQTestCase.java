package org.jboss.qa.hornetq.test;

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
import org.jboss.arquillian.test.spi.event.suite.BeforeClass;
import org.jboss.qa.hornetq.apps.servlets.KillerServlet;
import org.jboss.qa.tools.HornetQAdminOperationsEAP5;
import org.jboss.qa.tools.HornetQAdminOperationsEAP6;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.File;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Parent class for all HornetQ test cases. Provides an abstraction of used container
 *
 * @author pslavice@redhat.com
 * @author mnovak@redhat.com
 */
public class HornetQTestCase implements ContextProvider {

    // Logger
    private static final Logger log = Logger.getLogger(HornetQTestCase.class);

    // JBOSS_HOME properties
    public static String JBOSS_HOME_1;
    public static String JBOSS_HOME_2;
    public static String JBOSS_HOME_3;
    public static String JBOSS_HOME_4;

    // Containers IDs
    public static final String CONTAINER1 = "node-1";
    public static final String CONTAINER2 = "node-2";
    public static final String CONTAINER3 = "node-3";
    public static final String CONTAINER4 = "node-4";

    // IP address for containers
    public static String CONTAINER1_IP;
    public static String CONTAINER2_IP;
    public static String CONTAINER3_IP;
    public static String CONTAINER4_IP;

    // Name of the connection factory in JNDI
    public static String CONNECTION_FACTORY_JNDI_EAP5 = "/ConnectionFactory";
    public static String CONNECTION_FACTORY_JNDI_EAP6 = "jms/RemoteConnectionFactory";

    // Port for remote JNDI
    public static int PORT_JNDI_EAP5 = 1099;
    public static int PORT_JNDI_EAP6 = 4447;

    // Ports for Byteman
    public static final int BYTEMAN_CONTAINER1_PORT = 9091;
    public static final int BYTEMAN_CONTAINER2_PORT = 9191;

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

    // IDs for the active container definition
    protected static final String EAP5_CONTAINER = "EAP5 container";
    protected static final String EAP6_CONTAINER = "EAP6 container";

    @ArquillianResource
    protected ContainerController controller;

    @ArquillianResource
    protected Deployer deployer;

    // this property is initialized during BeforeClass phase by ArquillianConfiguration extension
    private static ArquillianDescriptor arquillianDescriptor;

    static {
        // Path to the journal
        String tmpJournalA = System.getProperty("JOURNAL_DIRECTORY_A");
        JOURNAL_DIRECTORY_A = (tmpJournalA != null) ? tmpJournalA : "../../../../hornetq-journal-A";
        String tmpJournalB = System.getProperty("JOURNAL_DIRECTORY_B");
        JOURNAL_DIRECTORY_B = (tmpJournalB != null) ? tmpJournalB : "../../../../hornetq-journal-B";

        // IP addresses for the servers
        CONTAINER1_IP = getEnvProperty("MYTESTIP_1");
        CONTAINER2_IP = getEnvProperty("MYTESTIP_2");
        CONTAINER3_IP = getEnvProperty("MYTESTIP_3");
        CONTAINER4_IP = getEnvProperty("MYTESTIP_4");
        String tmpMultiCastAddress = System.getProperty("MCAST_ADDR");
        MCAST_ADDRESS = tmpMultiCastAddress != null ? tmpMultiCastAddress : "233.3.3.3";

        JBOSS_HOME_1 = getEnvProperty("JBOSS_HOME_1");
        JBOSS_HOME_2 = getEnvProperty("JBOSS_HOME_2");
        JBOSS_HOME_3 = getEnvProperty("JBOSS_HOME_3");
        JBOSS_HOME_4 = getEnvProperty("JBOSS_HOME_4");
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

    /**
     * Returns context
     *
     * @param containerName name of the container
     * @return instance of {@link Context}
     * @throws NamingException if a naming exception is encountered
     */
    protected Context getContextByContainerName(String containerName) throws NamingException {

        if (containerName == null && "".equals(containerName)) {
            throw new IllegalStateException("Container name cannot be null or empty");
        }

        Context ctx = null;

        if (CONTAINER1.equals(containerName)) {
            getContext(CONTAINER1_IP);
        } else if (CONTAINER2.equals(containerName)) {
            getContext(CONTAINER2_IP);
        } else if (CONTAINER3.equals(containerName)) {
            getContext(CONTAINER3_IP);
        } else if (CONTAINER4.equals(containerName)) {
            getContext(CONTAINER4_IP);
        }

        return ctx;
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
        final Properties env = new Properties();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
        env.put(Context.PROVIDER_URL, String.format("remote://%s:%s", hostName, port));
        return new InitialContext(env);
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
        Properties properties = new Properties();
        properties.setProperty("java.naming.factory.initial", "org.jnp.interfaces.NamingContextFactory");
        properties.setProperty("java.naming.provider.url", "jnp://" + hostName + ":" + port);
        properties.setProperty("java.naming.factory.url.pkgs", "org.jnp.interfaces.NamingContextFactory");
        return new InitialContext(properties);
    }

    /**
     * Returns port for JNDI service
     *
     * @return returns default port for JNDI service
     */
    public int getJNDIPort() {
        int port = 0;
        if (isEAP5()) {
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
            eap6AdmOps.setPort(getJNDIPort());
            eap6AdmOps.connect();
            operations = eap6AdmOps;
        }
        return operations;
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
    public void setArquillianDescriptor(@Observes BeforeClass event, ArquillianDescriptor descriptor) {
        arquillianDescriptor = descriptor;
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
            log.debug("Killer servlet %s was not deployed and it ");
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
    public void stopServer(String containerName) {
        try {
            controller.stop(containerName);
        } catch (Exception ex) {
            log.debug("Stopping server " + ex.getMessage(), ex);
        }
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
        return (this.getCurrentContainerForTest() != null) && EAP5_CONTAINER.equals(this.getCurrentContainerForTest());
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
            }
        }
        return containerId;
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
     * Sets current container for test.
     *
     * @return name
     */
    public String getCurrentContainerForTest() {
        return currentContainerForTest;
    }

    public void setCurrentContainerForTest(String currentContainerForTest) {
        this.currentContainerForTest = currentContainerForTest;
    }
}
