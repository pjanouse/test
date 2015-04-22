package org.jboss.qa.hornetq.test.security;

import org.apache.activemq.api.core.TransportConfiguration;
import org.apache.activemq.api.core.client.ActiveMQClient;
import org.apache.activemq.api.core.client.ClientSession;
import org.apache.activemq.api.core.client.ClientSessionFactory;
import org.apache.activemq.api.core.client.ServerLocator;
import org.apache.activemq.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.core.remoting.impl.netty.TransportConstants;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.ContainerEAP7;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.NamingException;
import java.util.HashMap;

/**
 * Simple sender with auto acknowledge session. Able to fail over.
 * <p/>
 * This class extends Thread class and should be started as a thread using
 * start().
 *
 * @author mnovak
 */
public class SecurityClient extends Client {

    private static final Logger logger = Logger.getLogger(SecurityClient.class);
    private String hostname = "localhost";
    private int port = 4447;
    private String queueNameJndi = "jms/queue/testQueue1";
    private int messages = 1000;
    private MessageBuilder messageBuilder = new TextMessageBuilder(1000);
    private Exception exception = null;
    private int counter = 0;
    private boolean stop = false;
    private boolean securityEnabled = true;
    private String username = null;
    private String password = null;
    Context context = null;
    ConnectionFactory cf = null;
    Connection con = null;
    Session session = null;
    Queue queue = null;
    // core hq objects
    ClientSessionFactory coreClientSessionFactory = null;
    ClientSession coreClientSession = null;

    /**
     * @param container     EAP container
     * @param messages      number of messages to send
     * @param queueNameJndi set jndi name of the queue to send messages
     * @param username      username
     * @param password      password
     */
    public SecurityClient(Container container, String queueNameJndi, int messages, String username, String password) {
        super(container);
        this.hostname = container.getHostname();
        this.port = container.getJNDIPort();
        this.messages = messages;
        this.queueNameJndi = queueNameJndi;
        this.username = username;
        this.password = password;
    }
    /**
     * @param hostname      hostname
     * @param port          port
     * @param messages      number of messages to send
     * @param queueNameJndi set jndi name of the queue to send messages
     * @param username      username
     * @param password      password
     */
    @Deprecated
    public SecurityClient(String hostname, int port, String queueNameJndi, int messages, String username, String password) {
        this(EAP6_CONTAINER, hostname, port, queueNameJndi, messages, username, password);
    }

    /**
     * @param hostname      hostname
     * @param port          port
     * @param messages      number of messages to send
     * @param queueNameJndi set jndi name of the queue to send messages
     * @param username      username
     * @param password      password
     */
    @Deprecated
    public SecurityClient(String container, String hostname, int port, String queueNameJndi, int messages, String username, String password) {
        super(container);
        this.hostname = hostname;
        this.port = port;
        this.messages = messages;
        this.queueNameJndi = queueNameJndi;
        this.username = username;
        this.password = password;
    }

    /**
     * Initializes client with auto_ack session - creates connection and
     * session.
     *
     * @throws NamingException
     * @throws JMSException
     */
    public void initializeClient() throws Exception {

        context = getContext(hostname, port);

        cf = (ConnectionFactory) context.lookup(container.getConnectionFactoryName());

        con = getConnection();

        con.start();

        queue = (Queue) context.lookup(queueNameJndi);

        session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("host", hostname);
        map.put("port", container.getHornetqPort());
        map.put(TransportConstants.HTTP_UPGRADE_ENABLED_PROP_NAME, true);


        TransportConfiguration transportConfiguration = new TransportConfiguration(NettyConnectorFactory.class.getName(), map);

        ServerLocator locator = ActiveMQClient.createServerLocatorWithHA(transportConfiguration);

        coreClientSessionFactory = locator.createSessionFactory();

        coreClientSession = coreClientSessionFactory.createSession(username, password, false, true, true, false, 1024 * 1024);

    }

    public void consumeAndRollback(int numberOfMessagesToConsumeAndRollback) throws Exception {

        Session transSession = con.createSession(true, Session.SESSION_TRANSACTED);
        MessageConsumer consumer = transSession.createConsumer(queue);

        int numberOfConsumerMessageCounter = 0;

        Message msg;

        while (numberOfConsumerMessageCounter < numberOfMessagesToConsumeAndRollback) {

            msg = consumer.receive(1000);

            if (msg != null) {
                logger.info("Consumer for node: " + hostname + ". Received message with property count: " + counter + ", messageId:" + msg.getJMSMessageID() +
                        " message group id: " + msg != null ? msg.getStringProperty("JMSXGroupID") : null);
            } else {
                throw new Exception("Cannot receive " + numberOfMessagesToConsumeAndRollback + " messages. There are only " + numberOfConsumerMessageCounter + ". " +
                        "Check the reason why there is not enough messages in queue: " + queueNameJndi);
            }
            numberOfConsumerMessageCounter++;
        }

        transSession.rollback();

    }

    /**
     * Send and receive messages to/from server. This should be started -
     */
    public void sendAndReceive() throws Exception {
        con.start();

        MessageProducer producer = session.createProducer(queue);

        Message msg = null;

        while (counter < messages && !stop) {

            msg = messageBuilder.createMessage(session);
            // send message in while cycle
            producer.send(msg);

            counter++;

            logger.info("Producer for node: " + hostname + ". Sent message with property count: " + counter + ", messageId:" + msg.getJMSMessageID());

        }

        producer.close();

        MessageConsumer consumer = session.createConsumer(queue);

        counter = 0;

        while (counter < messages && !stop) {

            msg = consumer.receive(1000);

            counter++;

            logger.info("Consumer for node: " + hostname + ". Received message with property count: " + counter + ", messageId:" + msg.getJMSMessageID());

        }

        consumer.close();
    }

    public void send() throws Exception{
        con.start();

        MessageProducer producer = session.createProducer(queue);

        Message msg = null;

        while (counter < messages && !stop) {

            msg = messageBuilder.createMessage(session);
            // send message in while cycle
            producer.send(msg);

            counter++;

            logger.info("Producer for node: " + hostname + ". Sent message with property count: " + counter + ", messageId:" + msg.getJMSMessageID());

        }

        producer.close();


    }

    public void receive() throws Exception{
        con.start();
        Message msg = null;

        MessageConsumer consumer = session.createConsumer(queue);

        counter = 0;

        while ((msg = consumer.receive(1000))!=null && !stop) {
            counter++;

            logger.info("Consumer for node: " + hostname + ". Received message with property count: " + counter + ", messageId:" + msg.getJMSMessageID());

        }

        consumer.close();

    }

    /**
     * Close all resources.
     */
    public void close() {

        if (session != null) {
            try {
                session.close();
            } catch (JMSException e) {
            }
        }

        if (con != null) {
            try {
                con.close();
            } catch (JMSException e) {
            }
        }

        if (context != null) {
            try {
                context.close();
            } catch (NamingException e) {
            }
        }


        try {
            if (coreClientSessionFactory != null) {
                coreClientSessionFactory.close();
            }
        } catch (Exception ex) {
            // ignore
        }
        try {
            if (coreClientSession != null) {
                coreClientSession.close();
            }
        } catch (Exception ex) {
            // ignore
        }


    }

    /**
     * Create durable queue. Hornetq core api must be used.
     *
     * @param queueName
     * @return
     * @throws JMSException *
     */
    public void createDurableQueue(String queueName) throws Exception {

        coreClientSession.createQueue(queueName, queueName, true);

    }

    public void createNonDurableQueue(String queueName) throws Exception {

        coreClientSession.createQueue(queueName, queueName, false);

    }

    public void deleteDurableQueue(String queueName) throws Exception {

        deleteNonDurableQueue(queueName);

    }

    public void deleteNonDurableQueue(String queueName) throws Exception {

        coreClientSession.deleteQueue("jms.queue." + queueName);

    }

    /**
     * @return the hostname
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * @param hostname the hostname to set
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    /**
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * @param port the port to set
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * @return the queueNameJndi
     */
    public String getQueueNameJndi() {
        return queueNameJndi;
    }

    /**
     * @param queueNameJndi the queueNameJndi to set
     */
    public void setQueueNameJndi(String queueNameJndi) {
        this.queueNameJndi = queueNameJndi;
    }

    /**
     * @return the messages
     */
    public int getMessages() {
        return messages;
    }

    /**
     * @param messages the messages to set
     */
    public void setMessages(int messages) {
        this.messages = messages;
    }

    /**
     * @return the exception
     */
    public Exception getException() {
        return exception;
    }

    /**
     * @param exception the exception to set
     */
    public void setException(Exception exception) {
        this.exception = exception;
    }

    /**
     * Returns connection.
     *
     * @return connection
     * @throws JMSException
     */
    private Connection getConnection() throws JMSException {

        // if there is username and password and security enabled then use it
        if (securityEnabled && username != null && !"".equals(username) && password != null) {
            System.out.println("username: " + username + ", pass: " + password);
            return cf.createConnection(username, password);
        }
        // else it's guest user or security disabled
        return cf.createConnection();
    }

    /**
     * @return the securityEnabled
     */
    public boolean isSecurityEnabled() {
        return securityEnabled;
    }

    /**
     * @param securityEnabled the securityEnabled to set
     */
    public void setSecurityEnabled(boolean securityEnabled) {
        this.securityEnabled = securityEnabled;
    }

    /**
     * @return the username
     */
    public String getUserName() {
        return username;
    }

    /**
     * @param username the username to set
     */
    public void setUserName(String username) {
        this.username = username;
    }

    /**
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * @param password the password to set
     */
    public void setPassword(String password) {
        this.password = password;

    }
//    public int getCounter(){
//        return counter;
//    }

    public static void main(String[] args) throws Exception {
        Container container = new ContainerEAP7();

        SecurityClient producer = new SecurityClient(EAP7_CONTAINER, "127.0.0.1", 8080 , "jms/queue/testQueue0", 100, "admin", "adminadmin");

//        producer.setHostname("127.0.0.1");
//        producer.setPort(8080);
//        producer.setQueueNameJndi("jms/queue/testQueue0");
//        producer.setUserName("admin");
//        producer.setPassword("adminadmin");

        producer.initializeClient();

        producer.sendAndReceive();

//        producer.createDurableQueue("testQueue0");
//        producer.deleteDurableQueue("testQueue0");

        producer.createNonDurableQueue("jms.queue.aaa");
        producer.deleteNonDurableQueue("aaa");
        producer.close();

    }
}
