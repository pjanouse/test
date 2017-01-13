package org.jboss.qa.hornetq.test.prepares;

/**
 * Created by eduda on 28.11.2016.
 */
public class PrepareConstants {
    public static final String TOPIC_NAME = "testTopic";
    public static final String TOPIC_JNDI =  "jms/topic/" + TOPIC_NAME;
    public static final String QUEUE_NAME = "testQueue";
    public static final String QUEUE_JNDI =  "jms/queue/" + QUEUE_NAME;
    public static final String IN_QUEUE_NAME = "InQueue";
    public static final String IN_QUEUE_JNDI =  "jms/queue/" + IN_QUEUE_NAME;
    public static final String OUT_QUEUE_NAME = "OutQueue";
    public static final String OUT_QUEUE_JNDI =  "jms/queue/" + OUT_QUEUE_NAME;
    public static final String IN_TOPIC_NAME = "InTopic";
    public static final String IN_TOPIC_JNDI =  "jms/topic/" + IN_TOPIC_NAME;
    public static final String OUT_TOPIC_NAME = "OutTopic";
    public static final String OUT_TOPIC_JNDI =  "jms/topic/" + OUT_TOPIC_NAME;
    public static final String TOPIC_NAME_PREFIX = "testTopic";
    public static final String TOPIC_JNDI_PREFIX =  "jms/topic/" + TOPIC_NAME_PREFIX;
    public static final String QUEUE_NAME_PREFIX = "testQueue";
    public static final String QUEUE_JNDI_PREFIX =  "jms/queue/" + QUEUE_NAME_PREFIX;
    public static final String JMS_BRIDGE_NAME = "myBridge";
    public static final String CLUSTER_NAME = "my-cluster";
    public static final String REMOTE_CONNECTION_FACTORY_NAME = "RemoteConnectionFactory";
    public static final String REMOTE_CONNECTION_FACTORY_JNDI = "jms/RemoteConnectionFactory";
    public static final String INVM_CONNECTION_FACTORY_NAME = "InVmConnectionFactory";
    public static final String POOLED_CONNECTION_FACTORY_NAME_EAP6 = "hornetq-ra";
    public static final String POOLED_CONNECTION_FACTORY_NAME_EAP7 = "activemq-ra";
    public static final String DISCOVERY_GROUP_NAME = "dg-group1";
    public static final String BROADCAST_GROUP_NAME = "bg-group1";
    public static final String CONNECTOR_NAME_EAP7 = "http-connector";
    public static final String CONNECTOR_NAME_EAP6 = "netty";
    public static final String CONNECTOR_NAME = "connector";
    public static final String INVM_CONNECTOR_NAME = "in-vm";
    public static final String ACCEPTOR_NAME_EAP6 = "netty";
    public static final String ACCEPTOR_NAME_EAP7 = "http-acceptor";
    public static final String ACCEPTOR_NAME = "acceptor";
    public static final String ACCEPTOR_NAME_BACKUP = "acceptor-backup";
    public static final String JGROUPS_CHANNEL = "activemq-cluster";
    public static final String SERVER_NAME = "default";
    public static final String BACKUP_SERVER_NAME = "backup";
    public static final String MULTICAST_SOCKET_BINDING_NAME = "messaging-group";
    public static final String MESSAGING_SOCKET_BINDING_NAME = "messaging";
    public static final String MESSAGING_SOCKET_BINDING_NAME_BACKUP = "messaging-backup";
    public static final String USER_NAME = "user";
    public static final String USER_PASS = "useruser";
    public static final String ADMIN_NAME = "admin";
    public static final String ADMIN_PASS = "adminadmin";
    public static final String HTTP_SOCKET_BINDING = "http";
    public static final String HTTP_LISTENER = "default";
    public static final int MESSAGING_PORT = 5445;
    public static final int MESSAGING_PORT_BACKUP = 5446;
}
