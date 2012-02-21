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
 *
 * @author pslavice@redhat.com
 */
public class HornetQTestCase {

    // Logger
    private static final Logger log = Logger.getLogger(HornetQTestCase.class);

    // Arquillian container name
    protected static final String CONTAINER1 = "clustering-udp-0-unmanaged";

    // Arquillian container name
    protected static final String CONTAINER2 = "clustering-udp-1-unmanaged";

    // Name of the connection factory in JNDI
    protected static final String CONNECTION_FACTORY_JNDI = "jms/RemoteConnectionFactory";

    // Host for remote JNDI
    protected static final String HOST_NAME_JNDI = "localhost";

    // Port for remote JNDI
    protected static final int PORT_JNDI = 4447;
    
    // Journal directory for first live/backup pair or first node in cluster
    protected static final String JOURNAL_DIRECTORY_A = System.getProperty("JOURNAL_DIRECTORY_A") != null ? System.getProperty("JOURNAL_DIRECTORY_A") : "/tmp/hornetq-journal-A";
    
    // Journal directory for second live/backup pair or second node in cluster
    protected static final String JOURNAL_DIRECTORY_B = System.getProperty("JOURNAL_DIRECTORY_B") != null ? System.getProperty("JOURNAL_DIRECTORY_B") : "/tmp/hornetq-journal-B";


    @ArquillianResource
    protected ContainerController controller;

    /**
     * Cleanups resources
     *
     * @param context    initial context
     * @param connection connection to JMS server
     * @param session    JMS session
     */
    protected void cleanupResources(Context context, Connection connection, Session session) {
        if (session != null) {
            try {
                session.close();
            } catch (JMSException e) {
                // Ignore it
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (JMSException e) {
                // Ignore it
            }
        }
        if (context != null) {
            try {
                context.close();
            } catch (NamingException e) {
                // Ignore it
            }
        }
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
     * Returns context
     *
     * @return instance of {@link Context}
     * @throws NamingException if a naming exception is encountered
     */
    protected Context getContext() throws NamingException {
        return getContext(HOST_NAME_JNDI, PORT_JNDI);
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

    // TODO implement methods for getting client of required type, ack-mode etc.

}
