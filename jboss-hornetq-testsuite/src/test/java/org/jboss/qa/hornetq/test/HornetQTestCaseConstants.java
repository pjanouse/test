package org.jboss.qa.hornetq.test;

import org.jboss.qa.tools.ContainerInfo;

/**
 * Class containing constants used in tests and tools.
 */
public interface HornetQTestCaseConstants {

    // Containers IDs
    public static final String CONTAINER1 = "node-1";
    public static final String CONTAINER2 = "node-2";
    public static final String CONTAINER3 = "node-3";
    public static final String CONTAINER4 = "node-4";

    // Name of the connection factory in JNDI
    public static String CONNECTION_FACTORY_JNDI_EAP5 = "ConnectionFactory";
    public static String CONNECTION_FACTORY_JNDI_EAP6 = "jms/RemoteConnectionFactory";

    // Port for remote JNDI
    public static int PORT_JNDI_EAP5 = 1099;
    public static int PORT_JNDI_EAP6 = 4447;

    // Ports for Byteman
    public static final int BYTEMAN_CONTAINER1_PORT = 9091;
    public static final int BYTEMAN_CONTAINER2_PORT = 9191;
    public static final int BYTEMAN_CONTAINER3_PORT = 9291;
    public static final int BYTEMAN_CONTAINER4_PORT = 9391;

    // composite info objects for easier passing to utility classes
    public static final ContainerInfo CONTAINER1_INFO = new ContainerInfo(CONTAINER1, HornetQTestCase.CONTAINER1_IP,
            BYTEMAN_CONTAINER1_PORT);
    public static final ContainerInfo CONTAINER2_INFO = new ContainerInfo(CONTAINER2, HornetQTestCase.CONTAINER2_IP,
            BYTEMAN_CONTAINER2_PORT);
    public static final ContainerInfo CONTAINER3_INFO = new ContainerInfo(CONTAINER3, HornetQTestCase.CONTAINER3_IP,
            BYTEMAN_CONTAINER3_PORT);
    public static final ContainerInfo CONTAINER4_INFO = new ContainerInfo(CONTAINER4, HornetQTestCase.CONTAINER4_IP,
            BYTEMAN_CONTAINER4_PORT);

    // Management port - EAP 6
    public static int MANAGEMENT_PORT_EAP6 = 9999;

    // IDs for the active container definition
    public static final String EAP5_CONTAINER = "EAP5";
    public static final String EAP6_CONTAINER = "EAP6";
    public static final String EAP5_WITH_JBM_CONTAINER = "EAP5_WITH_JBM";

    // Timeout for CLI tests
    public static final int DEFAULT_TEST_TIMEOUT = 300000;


}
