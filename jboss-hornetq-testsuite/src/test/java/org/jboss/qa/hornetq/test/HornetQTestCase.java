package org.jboss.qa.hornetq.test;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.qa.hornetq.apps.servlets.KillerServlet;
import org.jboss.qa.tools.ConfigurationLoader;
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
 * Parent class for all HornetQ test cases.
 * <p/>
 * How to use this class: - class contains two properties with name of defined
 * containers
 * <code>CONTAINER1</code> and
 * <code>CONTAINER2</code> - class contains two properties with IP addresses
 * used in test
 * <code>CONTAINER1_IP</code> and
 * <code>CONTAINER2_IP</code>
 *
 * @author pslavice@redhat.com
 * @author mnovak@redhat.com
 */
public class HornetQTestCase implements ContextProvider {

    // Logger
    private static final Logger log = Logger.getLogger(HornetQTestCase.class);
    // Containers IDs
    protected static final String CONTAINER1 = ConfigurationLoader.CONTAINER1;
    protected static final String CONTAINER2 = ConfigurationLoader.CONTAINER2;
    protected static final String CONTAINER3 = ConfigurationLoader.CONTAINER3;
    protected static final String CONTAINER4 = ConfigurationLoader.CONTAINER4;

    // IP address for containers
    protected static String CONTAINER1_IP = ConfigurationLoader.CONTAINER1_IP;
    protected static String CONTAINER2_IP = ConfigurationLoader.CONTAINER2_IP;
    protected static String CONTAINER3_IP = ConfigurationLoader.CONTAINER3_IP;
    protected static String CONTAINER4_IP = ConfigurationLoader.CONTAINER4_IP;

    // Name of the connection factory in JNDI
    protected static final String CONNECTION_FACTORY_JNDI = ConfigurationLoader.CONNECTION_FACTORY_JNDI;
    // Port for remote JNDI
    protected static final int PORT_JNDI = ConfigurationLoader.PORT_JNDI;
    // Ports for Byteman
    protected static final int BYTEMAN_CONTAINER1_PORT = ConfigurationLoader.BYTEMAN_CONTAINER1_PORT;
    protected static final int BYTEMAN_CONTAINER2_PORT = ConfigurationLoader.BYTEMAN_CONTAINER2_PORT;
    // Multi-cast address
    protected static final String MCAST_ADDRESS = ConfigurationLoader.MCAST_ADDRESS;
    // Journal directory for first live/backup pair or first node in cluster
    protected static final String JOURNAL_DIRECTORY_A = ConfigurationLoader.JOURNAL_DIRECTORY_A;
    // Journal directory for second live/backup pair or second node in cluster
    protected static final String JOURNAL_DIRECTORY_B = ConfigurationLoader.JOURNAL_DIRECTORY_B;
    @ArquillianResource
    protected ContainerController controller;
    @ArquillianResource
    protected Deployer deployer;
    // Defined deployments - killer servlet which kills server
    // Artifact is not deployed on the server automatically, it is necessary to deploy it manually
    protected static final String SERVLET_KILLER_1 = "killerServlet1";
    protected static final String SERVLET_KILLER_2 = "killerServlet2";
    protected static final String SERVLET_KILLER_3 = "killerServlet3";
    protected static final String SERVLET_KILLER_4 = "killerServlet4";

    /**
     * Returns context
     *
     * @param containerName name of the container
     *
     * @return instance of {@link Context}
     *
     * @throws NamingException if a naming exception is encountered
     */
    protected Context getContextByContainerName(String containerName) throws NamingException {

        if (containerName == null && "".equals(containerName))  {
            throw new IllegalStateException("Container name cannot be null or empty");
        }

        Context ctx = null;

        if (CONTAINER1.equals(containerName))   {
            getContext(CONTAINER1_IP);
        } else if (CONTAINER2.equals(containerName))    {
            getContext(CONTAINER2_IP);
        } else if (CONTAINER3.equals(containerName))    {
            getContext(CONTAINER3_IP);
        } else if (CONTAINER4.equals(containerName))    {
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
    protected Context getContext(String hostName, int port) throws NamingException {

        Context ctx;

        try {         // try EAP 6 context
            ctx = getEAP6Context(hostName, port);
        } catch (NamingException ex)  {      // try EAP 5 context
            ctx = getEAP5Context(hostName, 1099);
        }

        return ctx;
    }

    private Context getEAP6Context(String hostName, int port) throws NamingException {
        final Properties env = new Properties();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
        env.put(Context.PROVIDER_URL, String.format("remote://%s:%s", hostName, port));
        return new InitialContext(env);
    }

    private Context getEAP5Context(String hostName, int port) throws NamingException {

        Properties properties = new Properties();
        properties.setProperty("java.naming.factory.initial", "org.jnp.interfaces.NamingContextFactory");
        properties.setProperty("java.naming.provider.url", "jnp://" + hostName + ":" + port);
        properties.setProperty("java.naming.factory.url.pkgs", "org.jnp.interfaces.NamingContextFactory");
        return new InitialContext(properties);
    }

    /**
     * @see org.jboss.qa.hornetq.test.ContextProvider#getContext()
     */
    public Context getContext() throws NamingException {
        return getContext(CONTAINER1_IP, PORT_JNDI);
    }

    /**
     * @see org.jboss.qa.hornetq.test.ContextProvider#getContextContainer1()
     */
    public Context getContextContainer1() throws NamingException {
        return getContext(CONTAINER1_IP, PORT_JNDI);
    }

    /**
     * @see org.jboss.qa.hornetq.test.ContextProvider#getContextContainer2()
     */
    public Context getContextContainer2() throws NamingException {
        return getContext(CONTAINER2_IP, PORT_JNDI);
    }

    /**
     * Deletes given folder and all sub folders
     *
     * @param path folder which should be deleted
     * @return true if operation was successful, false otherwise
     */
    protected boolean deleteFolder(File path) {
        log.info(String.format("Removing folder '%s'", path));
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
     * Deletes data folder for given JBoss home, removes standalone data folder
     * for standalone profile
     *
     * @param jbossHome JBoss home folder
     * @return true if operation was successful, false otherwise
     */
    protected boolean deleteDataFolder(String jbossHome) {
        return deleteFolder(new File(jbossHome + "/standalone/data"));
    }

    /**
     * Deletes data folder for given JBoss home - system property JBOSS_HOME_1,
     * removes standalone data folder for standalone profile.
     *
     * @return true if operation was successful, false otherwise
     */
    protected boolean deleteDataFolderForJBoss1() {
        return deleteDataFolder(System.getProperty("JBOSS_HOME_1"));
    }

    /**
     * Deletes data folder for given JBoss home - system property JBOSS_HOME_2,
     * removes standalone data folder for standalone profile.
     *
     * @return true if operation was successful, false otherwise
     */
    protected boolean deleteDataFolderForJBoss2() {
        return deleteDataFolder(System.getProperty("JBOSS_HOME_2"));
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
     *
     * This is wrapper method for controller.stop() method. Problem is that in EAP 5 this method throws exception
     * when server is not running. We'll swallow this exception.
     *
     *
     * @param containerName name of the container
     *
     */
    public void stopServer(String containerName)    {

        try {

            controller.stop(containerName);

        } catch (Exception ignored) {} // swallow it

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
}
