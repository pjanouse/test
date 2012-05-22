package org.jboss.qa.hornetq.test;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.qa.hornetq.apps.servlets.KillerServlet;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;

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
 */
public class HornetQTestCase implements ContextProvider {

    // Logger
    private static final Logger log = Logger.getLogger(HornetQTestCase.class);
    // Arquillian container name
    protected static final String CONTAINER1 = "node-1";
    // IP address for container 1
    protected static String CONTAINER1_IP;
    // Arquillian container name
    protected static final String CONTAINER2 = "node-2";
    // IP address for container 2
    protected static String CONTAINER2_IP;
    // Arquillian container name
    protected static final String CONTAINER3 = "node-3";
    // IP address for container 3
    protected static String CONTAINER3_IP;
    // Arquillian container name
    protected static final String CONTAINER4 = "node-4";
    // IP address for container 4
    protected static String CONTAINER4_IP;
    // Name of the connection factory in JNDI
    protected static final String CONNECTION_FACTORY_JNDI = "jms/RemoteConnectionFactory";
    // Host for remote JNDI
    protected static final String HOST_NAME_JNDI = "localhost";
    // Port for remote JNDI
    protected static final int PORT_JNDI = 4447;
    // Ports for Byteman
    protected static final int BYTEMAN_CONTAINER1_PORT = 9091;
    protected static final int BYTEMAN_CONTAINER2_PORT = 9191;
    protected static final int BYTEMAN_CONTAINER3_PORT = 9291;
    protected static final int BYTEMAN_CONTAINER4_PORT = 9391;
    // Multi-cast address
    protected static final String MULTICAST_ADDRESS;
    // Journal directory for first live/backup pair or first node in cluster
    protected static final String JOURNAL_DIRECTORY_A;
    // Journal directory for second live/backup pair or second node in cluster
    protected static final String JOURNAL_DIRECTORY_B;
    @ArquillianResource
    protected ContainerController controller;
    @ArquillianResource
    protected Deployer deployer;

    static {
        if (System.getProperty("MYTESTIP_1") != null) {
            CONTAINER1_IP = System.getProperty("MYTESTIP_1");
            log.info(String.format("Setting CONTAINER1_IP='%s'", CONTAINER1_IP));
        }
        if (System.getProperty("MYTESTIP_2") != null) {
            CONTAINER2_IP = System.getProperty("MYTESTIP_2");
            log.info(String.format("Setting CONTAINER2_IP='%s'", CONTAINER2_IP));
        }
        if (System.getProperty("MYTESTIP_3") != null) {
            CONTAINER3_IP = System.getProperty("MYTESTIP_3");
            log.info(String.format("Setting CONTAINER3_IP='%s'", CONTAINER3_IP));
        }
        if (System.getProperty("MYTESTIP_4") != null) {
            CONTAINER4_IP = System.getProperty("MYTESTIP_4");
            log.info(String.format("Setting CONTAINER4_IP='%s'", CONTAINER4_IP));
        }
        MULTICAST_ADDRESS = System.getProperty("MCAST_ADDR") != null ? System.getProperty("MCAST_ADDR") : "233.3.3.3";
        JOURNAL_DIRECTORY_A = System.getProperty("JOURNAL_DIRECTORY_A") != null ? System.getProperty("JOURNAL_DIRECTORY_A") : "../../../../hornetq-journal-A";
        JOURNAL_DIRECTORY_B = System.getProperty("JOURNAL_DIRECTORY_B") != null ? System.getProperty("JOURNAL_DIRECTORY_B") : "../../../../hornetq-journal-B";
    }

    /**
     * Returns context
     *
     * @param hostName target hostname with JNDI service
     * @param port port on the target service
     * @return instance of {@link Context}
     * @throws NamingException if a naming exception is encountered
     */
    protected Context getContext(String hostName, int port) throws NamingException {
        final Properties env = new Properties();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
        env.put(Context.PROVIDER_URL, String.format("remote://%s:%s", hostName, port));
        return new InitialContext(env);
    }

    /**
     * @see org.jboss.qa.hornetq.test.ContextProvider#getContext()
     */
    public Context getContext() throws NamingException {
        return getContext(HOST_NAME_JNDI, PORT_JNDI);
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
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    successful = successful && deleteFolder(files[i]);
                } else {
                    successful = successful && files[i].delete();
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
     * anything else. It doesn't call controller.kill(contanerName).
     * @param ContainerName
     * @param ContainerIpAddress
     */
    protected void killServer(String ContainerName) {

        log.info("Killing server: " + ContainerName);
        try {
            if (CONTAINER1.equals(ContainerName)) {
                deployer.deploy("killerServlet1");
                HttpRequest.get("http://" + CONTAINER1_IP + ":8080/KillerServlet/KillerServlet?op=kill", 4, TimeUnit.SECONDS);
//                controller.kill(CONTAINER1);
            } else if (CONTAINER2.equals(ContainerName)) {
                deployer.deploy("killerServlet2");
                HttpRequest.get("http://" + CONTAINER2_IP + ":8080/KillerServlet/KillerServlet?op=kill", 4, TimeUnit.SECONDS);
//                controller.kill(CONTAINER2);
            } else if (CONTAINER3.equals(ContainerName)) {
                deployer.deploy("killerServlet3");
                HttpRequest.get("http://" + CONTAINER3_IP + ":8080/KillerServlet/KillerServlet?op=kill", 4, TimeUnit.SECONDS);
//                controller.kill(CONTAINER3);
            } else if (CONTAINER4.equals(ContainerName)) {
                deployer.deploy("killerServlet4");
                HttpRequest.get("http://" + CONTAINER4_IP + ":8080/KillerServlet/KillerServlet?op=kill", 4, TimeUnit.SECONDS);
//                controller.kill(CONTAINER4);
            } else {
                throw new RuntimeException("Name of the container: " + ContainerName + " for killerServlet is not known. "
                        + "It can't be deployed");
            }
        } catch (Exception ex) {
            // ignore
            //ex.printStackTrace();
        } finally {
            log.info("Server: " + ContainerName + " -- KILLED");
        }
    }

    @Deployment(managed = false, testable = false, name = "killerServlet1")
    @TargetsContainer(CONTAINER1)
    public static WebArchive getDeploymentKilServletContainer1() throws Exception {
        return createKillerServlet();
    }

    @Deployment(managed = false, testable = false, name = "killerServlet2")
    @TargetsContainer(CONTAINER2)
    public static WebArchive getDeploymentKilServletContainer2() throws Exception {
        return createKillerServlet();
    }

    @Deployment(managed = false, testable = false, name = "killerServlet3")
    @TargetsContainer(CONTAINER3)
    public static WebArchive getDeploymentKilServletContainer3() throws Exception {
        return createKillerServlet();
    }

    @Deployment(managed = false, testable = false, name = "killerServlet4")
    @TargetsContainer(CONTAINER4)
    public static WebArchive getDeploymentKilServletContainer4() throws Exception {
        return createKillerServlet();
    }

    private static WebArchive createKillerServlet() {

        final WebArchive killerServlet = ShrinkWrap.create(WebArchive.class, "killerServlet.war");
        StringBuilder webXml = new StringBuilder();
        webXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> ");
        webXml.append("<web-app version=\"2.5\" xmlns=\"http://java.sun.com/xml/ns/javaee\" \n");
        webXml.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n");
        webXml.append("xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee \n");
        webXml.append("http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd\">\n");
        webXml.append("<servlet><servlet-name>KillerServlet</servlet-name>\n");
        webXml.append("<servlet-class>org.jboss.qa.hornetq.apps.servlets.KillerServlet</servlet-class></servlet>\n");
        webXml.append("<servlet-mapping><servlet-name>KillerServlet</servlet-name>\n");
        webXml.append("<url-pattern>/KillerServlet</url-pattern>\n");
        webXml.append("</servlet-mapping>\n");
        webXml.append("</web-app>\n");
        log.debug(webXml.toString());
        killerServlet.addAsWebInfResource(new StringAsset(webXml.toString()), "web.xml");

        StringBuilder jbossWebXml = new StringBuilder();
        jbossWebXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> \n");
        jbossWebXml.append("<jboss-web> \n");
        jbossWebXml.append("<context-root>/KillerServlet</context-root> \n");
        jbossWebXml.append("</jboss-web> \n");
        log.debug(jbossWebXml.toString());
        killerServlet.addAsWebInfResource(new StringAsset(jbossWebXml.toString()), "jboss-web.xml");
        killerServlet.addClass(KillerServlet.class);
        log.info(killerServlet.toString(true));
//      Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/KillerServlet.war");
//        if (target.exists()) {
//            target.delete();
//        }
//        killerServlet.as(ZipExporter.class).exportTo(target, true);

        return killerServlet;
    }
}
