package org.jboss.qa.hornetq.constants;

/**
 * Created by mnovak on 4/1/15.
 */
public class Constants {

    // COMMON CONSTANTS
    public static final int DEFAULT_PORT_OFFSET_INTERVAL = 1000;
    public static final int DEFAULT_BYTEMAN_PORT = 9091;

    // EAP 6
    public static final int PORT_HORNETQ_DEFAULT_EAP6 = 5445;
    public static final int PORT_HORNETQ_BACKUP_DEFAULT_EAP6 = 5446;
    public static final int MANAGEMENT_PORT_DEFAULT_EAP6 = 9999;
    public static final int JNDI_PORT_DEFAULT_EAP6 = 4447;

    public static final String CONNECTION_FACTORY_EAP6 = "RemoteConnectionFactory";
    public static final String CONNECTION_FACTORY_JNDI_EAP6 = "jms/" + CONNECTION_FACTORY_EAP6;
    public static final String CONNECTION_FACTORY_JNDI_FULL_NAME_EAP6 = "java:jboss/exported/jms/" + CONNECTION_FACTORY_EAP6;
    public static final String POOLED_CONNECTION_FACTORY_JNDI_EAP6 = "java:/JmsXA";
    public static final String RESOURCE_ADAPTER_NAME_EAP6 = "hornetq-ra";

    public static final String INITIAL_CONTEXT_FACTORY_EAP6 = "org.jboss.naming.remote.client.InitialContextFactory";
    public static final String PROVIDER_URL_PROTOCOL_PREFIX_EAP6 = "remote://";

    // EAP 7
    public static final int MANAGEMENT_PORT_DEFAULT_EAP7 = 9990;
    public static final int PORT_HORNETQ_DEFAULT_EAP7 = 8080;
    public static final int LEGACY_PORT_HORNETQ_DEFAULT_EAP7 = 5445;
    public static final int PORT_HORNETQ_DEFAULT_BACKUP_EAP7 = 8080;
    public static final int JNDI_PORT_DEFAULT_EAP7 = 8080;
    public static final String CONNECTION_FACTORY_EAP7 = "RemoteConnectionFactory";
    public static final String CONNECTION_FACTORY_JNDI_EAP7 = "jms/" + CONNECTION_FACTORY_EAP7;
    public static final String CONNECTION_FACTORY_JNDI_FULL_NAME_EAP7 = "java:jboss/exported/jms/" + CONNECTION_FACTORY_EAP7;
    public static final String POOLED_CONNECTION_FACTORY_JNDI_EAP7 = "java:/JmsXA";
    public static final String RESOURCE_ADAPTER_NAME_EAP7 = "activemq-ra";
    public static final String IN_VM_CONNECTION_FACTORY_EAP7 = "InVmConnectionFactory";

    public static final String INITIAL_CONTEXT_FACTORY_EAP7 = "org.jboss.naming.remote.client.InitialContextFactory";
    public static final String PROVIDER_URL_PROTOCOL_PREFIX_EAP7 = "http-remoting://";

    // used in LodhNetworkFailureTestCase and MdbToDBAndRemoteInOutQueue
    public static final String TO_OUT_SERVER_CONNECTION_FACTORY_NAME = "ra-to-out-server";
    public static final String TO_OUT_SERVER_CONNECTION_FACTORY_JNDI_NAME = "java:/JmsXAOutServer";

    public enum FAILURE_TYPE {
        KILL,
        SHUTDOWN,
        OUT_OF_MEMORY_HEAP_SIZE,
        OUT_OF_MEMORY_UNABLE_TO_OPEN_NEW_NATIE_THREAD
    }

    public enum CONTAINER_TYPE {
        EAP5_CONTAINER, EAP6_CONTAINER, EAP5_WITH_JBM_CONTAINER, EAP6_LEGACY_CONTAINER, EAP6_DOMAIN_CONTAINER, EAP7_CONTAINER, EAP7_DOMAIN_CONTAINER;
    }

    public enum CONNECTOR_TYPE {
        NETTY_BIO,
        NETTY_NIO,
        HTTP_CONNECTOR,
        NETTY_DISCOVERY,
        JGROUPS_DISCOVERY
    }

    public enum JOURNAL_TYPE
    {
        ASYNCIO, NIO
    }

    public enum QUALITY_OF_SERVICE
    {
        ONCE_AND_ONLY_ONCE, AT_MOST_ONCE, DUPLICATES_OK
    }

}
