package org.jboss.qa.tools;

import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;

/**
 *
 * @author mnovak@redhat.com
 */
public class ConfigurationLoader {

    // Logger
    private static final Logger log = Logger.getLogger(ConfigurationLoader.class);
    // this property is initialized during BeforeClass phase by ArquillianConfiguration extension
    public static ArquillianDescriptor descriptor;
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
    public static String CONNECTION_FACTORY_JNDI = "jms/RemoteConnectionFactory";
    // Port for remote JNDI
    public static int PORT_JNDI = 4447;
    // Ports for Byteman
    public static final int BYTEMAN_CONTAINER1_PORT = 9091;
    public static final int BYTEMAN_CONTAINER2_PORT = 9191;
    // Multi-cast address
    public static final String MCAST_ADDRESS;
    // Journal directory for first live/backup pair or first node in cluster
    public static final String JOURNAL_DIRECTORY_A;
    // Journal directory for second live/backup pair or second node in cluster
    public static final String JOURNAL_DIRECTORY_B;

    static {
        // Path to the journal
        String tmpJournalA = System.getProperty("JOURNAL_DIRECTORY_A");
        JOURNAL_DIRECTORY_A = (tmpJournalA != null) ? tmpJournalA : "../../../../hornetq-journal-A";
        log.info("JOURNAL_DIRECTORY_A=" + JOURNAL_DIRECTORY_A);
        String tmpJournalB = System.getProperty("JOURNAL_DIRECTORY_B");
        JOURNAL_DIRECTORY_B = (tmpJournalB != null) ? tmpJournalB : "../../../../hornetq-journal-B";

        // IP addresses for the servers
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
        MCAST_ADDRESS = System.getProperty("MCAST_ADDR") != null ? System.getProperty("MCAST_ADDR") : "233.3.3.3";

        if (System.getProperty("JBOSS_HOME_1") != null) {
            JBOSS_HOME_1 = System.getProperty("JBOSS_HOME_1");
            log.info(String.format("Setting JBOSS_HOME_1='%s'", JBOSS_HOME_1));
        }

        if (System.getProperty("JBOSS_HOME_2") != null) {
            JBOSS_HOME_2 = System.getProperty("JBOSS_HOME_2");
            log.info(String.format("Setting JBOSS_HOME_2='%s'", JBOSS_HOME_2));
        }

        if (System.getProperty("JBOSS_HOME_3") != null) {
            JBOSS_HOME_3 = System.getProperty("JBOSS_HOME_3");
            log.info(String.format("Setting JBOSS_HOME_3='%s'", JBOSS_HOME_3));
        }

        if (System.getProperty("JBOSS_HOME_4") != null) {
            JBOSS_HOME_4 = System.getProperty("JBOSS_HOME_4");
            log.info(String.format("Setting JBOSS_HOME_1='%s'", JBOSS_HOME_4));
        }
    }

    /**
     * Gets name of the profile. Related only to EAP 5.
     *
     * @param containerName name of the container
     * @return Name of the profile as specified in arquillian.xml for
     * profileName or "default" if not set.
     */
    public static String getProfile(String containerName) {
        for (GroupDef groupDef : descriptor.getGroups()) {
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
     * Returns JBOSS_HOME for the given container.
     *
     * @param containerName name of the container
     * @return JBOSS_HOME or null if not specified
     */
    public static String getJbossHome(String containerName) {
        for (GroupDef groupDef : descriptor.getGroups()) {
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
        for (GroupDef groupDef : descriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                if (containerDef.getContainerName().equalsIgnoreCase(containerName)) {
                    if (containerDef.getContainerProperties().containsKey("bindAddress")) {
                        return containerDef.getContainerProperties().get("bindAddress");
                    }
                }
            }
        }
        for (GroupDef groupDef : descriptor.getGroups()) {
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
     * Return managementPort or rmiPort as defined in arquillian.xml. For EAP 5.
     * 
     * @param containerName  name of the container
     * @return managementPort or rmiPort or 9999 when not set
     */
    public static int getRmiPort(String containerName) {
        for (GroupDef groupDef : descriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                if (containerDef.getContainerName().equalsIgnoreCase(containerName)) {
                    if (containerDef.getContainerProperties().containsKey("rmiPort")) {
                        return Integer.valueOf(containerDef.getContainerProperties().get("rmiPort"));
                    }
                }
            }
        }

        return 1099;
    }
    
    /**
     * Return managementPort as defined in arquillian.xml.
     * 
     * @param containerName name of the container
     * @return managementPort or rmiPort or 9999 when not set
     */
    public static int getPort(String containerName) {
        
        for (GroupDef groupDef : descriptor.getGroups()) {
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
}
