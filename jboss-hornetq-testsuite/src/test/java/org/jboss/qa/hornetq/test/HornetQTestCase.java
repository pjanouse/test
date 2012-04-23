package org.jboss.qa.hornetq.test;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.test.api.ArquillianResource;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.File;
import java.util.Properties;

/**
 * Parent class for all HornetQ test cases.
 * <p/>
 * How to use this class:
 * - class contains two properties with name of defined containers
 * <code>CONTAINER1</code> and <code>CONTAINER2</code>
 * - class contains two properties with IP addresses used in test
 * <code>CONTAINER1_IP</code> and <code>CONTAINER2_IP</code>
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
     * @param port     port on the target service
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
     * Deletes data folder for given JBoss home, removes standalone
     * data folder for standalone profile
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

}
