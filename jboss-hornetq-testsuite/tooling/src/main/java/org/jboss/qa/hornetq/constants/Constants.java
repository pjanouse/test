package org.jboss.qa.hornetq.constants;

/**
 * Created by mnovak on 4/1/15.
 */
public class Constants {

    // COMMON CONSTANTS
    public static final int DEFAULT_PORT_OFFSET_INTERVAL = 1000;

    // EAP 6
    public static final int PORT_HORNETQ_DEFAULT_EAP6 = 5445;
    public static final int PORT_HORNETQ_BACKUP_DEFAULT_EAP6 = 5446;
    public static final int MANAGEMENT_PORT_DEFAULT_EAP6 = 9999;
    public static final int JNDI_PORT_DEFAULT_EAP6 = 4447;
    public static final int DEFAULT_BYTEMAN_PORT = 9091;
    public static final String CONNECTION_FACTORY_JNDI_EAP6 = "jms/RemoteConnectionFactory";

    // EAP 7
    public static final int MANAGEMENT_PORT_DEFAULT_EAP7 = 9990;
    public static final int BYTEMAN_PORT = 9091;
    public static final int PORT_HORNETQ_DEFAULT = 9990;
    public static final int PORT_HORNETQ_DEFAULT_BACKUP = 9990;
    public static final int JNDI_PORT_DEFAULT_EAP7 = 8080;
    public static final String CONNECTION_FACTORY_JNDI_EAP7 = "jms/RemoteConnectionFactory";

}
