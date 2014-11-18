package org.jboss.qa.hornetq;

/**
 * Class containing constants used in tests and tools.
 */
public interface HornetQTestCaseConstants {

    // Containers IDs
    public static final String CONTAINER1 = "node-1";
    public static final String CONTAINER2 = "node-2";
    public static final String CONTAINER3 = "node-3";
    public static final String CONTAINER4 = "node-4";

    // Server groups (EAP6 domain only)
    public static final String SERVER_GROUP1 = "server-group-1";
    public static final String SERVER_GROUP2 = "server-group-2";
    public static final String SERVER_GROUP3 = "server-group-3";
    public static final String SERVER_GROUP4 = "server-group-4";

    // Name of the connection factory in JNDI
    public static String CONNECTION_FACTORY_JNDI_EAP5 = "ConnectionFactory";
    public static String CONNECTION_FACTORY_JNDI_EAP6 = "jms/RemoteConnectionFactory";
    public static String CONNECTION_FACTORY_JNDI_EAP6_FULL_NAME = "java:/jboss/exported/jms/RemoteConnectionFactory";

    // Port for remote JNDI
    public static int PORT_JNDI_EAP5 = 1099;
    public static int PORT_JNDI_EAP6 = 4447;

    // Port for HornetQ
    public static int PORT_HORNETQ_DEFAULT = 5445;
    public static int PORT_HORNETQ_BACKUP_DEFAULT = 5446;

    // Ports for Byteman
    public static final int BYTEMAN_CONTAINER1_PORT = 9091;
    public static final int BYTEMAN_CONTAINER2_PORT = 9191;
    public static final int BYTEMAN_CONTAINER3_PORT = 9291;
    public static final int BYTEMAN_CONTAINER4_PORT = 9391;

    // Management port - EAP 6
    public static int MANAGEMENT_PORT_EAP6 = 9999;

    // IDs for the active container definition
    public static final String EAP5_CONTAINER = "EAP5_CONTAINER";
    public static final String EAP6_CONTAINER = "EAP6_CONTAINER";
    public static final String EAP6_LEGACY_CONTAINER = "EAP6_LEGACY_CONTAINER";
    public static final String EAP6_DOMAIN_CONTAINER = "EAP6_DOMAIN_CONTAINER";
    public static final String EAP5_WITH_JBM_CONTAINER = "EAP5_WITH_JBM_CONTAINER";

    public enum CONTAINER_TYPE {
        EAP5_CONTAINER, EAP6_CONTAINER, EAP5_WITH_JBM_CONTAINER, EAP6_LEGACY_CONTAINER, EAP6_DOMAIN_CONTAINER
    }

    // Timeout for CLI tests
    public static final int DEFAULT_TEST_TIMEOUT = 300000;

    public static final String URL_JDBC_DRIVERS = "http://www.qa.jboss.com/jdbc-drivers-products/EAP";

    // MUST be the same names as in DBAllocator - http://dballocator.mw.lab.eng.bos.redhat.com:8080/Allocator/AllocatorServlet?operation=report
    public static final String ORACLE11GR2 = "oracle11gR2";
    public static final String ORACLE12C = "oracle12c";
    public static final String ORACLE11GR1 = "oracle11gR1";
    public static final String MYSQL55 = "mysql55";
    public static final String MYSQL57 = "mysql57";
    public static final String POSTGRESQL92 ="postgresql92";
    public static final String POSTGRESQL93 ="postgresql93";
    public static final String POSTGRESQLPLUS92 = "postgresplus92";
    public static final String POSTGRESQLPLUS93 = "postgresplus93";
    public static final String MSSQL2014 = "mssql2014";
    public static final String MSSQL2012 = "mssql2012";
    public static final String MSSQL2008R2 = "mssql2008R2";
    public static final String DB2105 = "db2-105";
    public static final String SYBASE157 ="sybase157";

    // type of messages
    public static final String SMALL_MESSAGES = "small";
    public static final String SMALL_MESSAGES_WITH_DUP_ID = "smallWithDupId";
    public static final String LARGE_MESSAGES = "large";
    public static final String LARGE_MESSAGES_WITH_DUP_ID = "largeWithDupId";
    public static final String MIXED_MESSAGES = "mixed";

    // type of destination
    public static final String QUEUE_DESTINATION_TYPE = "queue";
    public static final String TOPIC_DESTINATION_TYPE = "topic";


}
