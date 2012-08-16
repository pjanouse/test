package org.jboss.qa.hornetq.test;

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

    // IDs for the active container definition
    public static final String EAP5_CONTAINER = "EAP5";
    public static final String EAP6_CONTAINER = "EAP6";


}
