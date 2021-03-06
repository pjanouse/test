package org.jboss.qa.hornetq;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.DebugTools;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.junit.Assert;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

/**
 * Utilities for JMS org.jboss.qa.hornetq.apps.clients
 */
public final class JMSTools {

    private static final Logger log = Logger.getLogger(JMSTools.class);

    private JMSTools() {}

    /**
     * Cleanups resources
     *
     * @param context    initial context
     * @param connection connection to JMS server
     * @param session    JMS session
     */
    public static void cleanupResources(Context context, Connection connection, Session session) {
        if (session != null) {
            try {
                session.close();
            } catch (JMSException e) {
                log.error("Error while trying to close JMS session", e);
            }
        }
        if (connection != null) {
            try {
                connection.stop();
                connection.close();
            } catch (JMSException e) {
                log.error("Error while trying to close JMS connection", e);
            }
        }
        if (context != null) {
            try {
                context.close();
            } catch (NamingException e) {
                log.error("Error while trying to close naming context", e);
            }
        }
    }

    /**
     * Returns EAP 6 context
     *
     * @param hostName host name
     * @param port     target port with the service
     * @return instance of the context
     * @throws NamingException if something goes wrong
     */
    public static Context getEAP6Context(String hostName, int port, Constants.JNDI_CONTEXT_TYPE contextType) throws NamingException {
        final Properties env = new Properties();
        env.put(Context.INITIAL_CONTEXT_FACTORY, Constants.INITIAL_CONTEXT_FACTORY_EAP6);
        env.put(Context.PROVIDER_URL, String.format("%s%s:%s", Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP6, hostName, port));
        if (Constants.JNDI_CONTEXT_TYPE.EJB_CONTEXT.equals(contextType)) {
            env.put(Constants.CLIENT_EJB_CONTEXT_PROPERTY_EAP6, true);
        }
        return new InitialContext(env);
    }

    /**
     * Waits until sum of messages in queue in containers contains the given number
     * of messages
     *
     * @param queueName        queue name
     * @param numberOfMessages number of messages
     * @param timeout          time out
     * @param containers       container list
     * @return returns true if there is numberOfMessages in queue, when timeout
     * expires it returns false
     * @throws Exception
     */
    public static boolean waitForMessages(String queueName, long numberOfMessages, long timeout, org.jboss.qa.hornetq.Container... containers) throws Exception {

        long startTime = System.currentTimeMillis();

        long count = 0;
        while ((count = countMessages(queueName, containers)) < numberOfMessages) {
            List<String> containerNames = new ArrayList<String>(containers.length);
            for (org.jboss.qa.hornetq.Container c : containers) {
                containerNames.add(c.getName());
            }

            log.info("Total number of messages in queue: " + queueName + " on node "
                    + Arrays.toString(containerNames.toArray()) + " is " + count);
            Thread.sleep(5000);
            if (System.currentTimeMillis() - startTime > timeout) {
                return false;
            }
        }
        return true;
    }

    /**
     * Waits until number of added messages in queue in containers exceeeds given number
     * of messages
     *
     * @param queueName        queue name
     * @param numberOfMessages number of messages
     * @param timeout          time out
     * @param containers       container list
     * @return returns true if there is numberOfMessages in queue, when timeout
     * expires it returns false
     * @throws Exception
     * @see JMSTools#getAddedMessagesCount(String, Container...)
     */
    public boolean waitForAddedMessages(String queueName, long numberOfMessages, long timeout, org.jboss.qa.hornetq.Container... containers) throws Exception {

        long startTime = System.currentTimeMillis();

        long count = 0;
        while ((count = getAddedMessagesCount(queueName, containers)) < numberOfMessages) {
            List<String> containerNames = new ArrayList<String>(containers.length);
            for (org.jboss.qa.hornetq.Container c : containers) {
                containerNames.add(c.getName());
            }

            log.info("Total number of added messages in queue: " + queueName + " on node "
                    + Arrays.toString(containerNames.toArray()) + " is " + count);
            Thread.sleep(5000);
            if (System.currentTimeMillis() - startTime > timeout) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns total number of messages in queue on given nodes
     *
     * @param queueName  queue name
     * @param containers container list
     * @return total number of messages in queue on given nodes
     */
    public static long countMessages(String queueName, org.jboss.qa.hornetq.Container... containers) {
        long sum = 0;
        for (org.jboss.qa.hornetq.Container container : containers) {
            JMSOperations jmsOperations = container.getJmsOperations();
            long count = -1;
            int numberOfTries = 0;
            int maxNumberOfTries = 10;
            while (count == -1 && numberOfTries < maxNumberOfTries) {
                try {
                    numberOfTries++;
                    count = jmsOperations.getCountOfMessagesOnQueue(queueName);
                    break;
                } catch (Exception ex) {
                    if (numberOfTries > maxNumberOfTries - 1) {
                        throw new RuntimeException("getCountOfMessagesOnQueue() failed for queue:" + queueName
                                + " and container: " + container.getName() + ". Number of tries: " + numberOfTries, ex);
                    }
                }
            }
            log.info("Number of messages on node : " + container.getName() + " is: " + count);
            sum += count;
            jmsOperations.close();
        }
        log.info("Sum of messages on nodes is: " + sum);
        return sum;
    }

    /**
     * Returns total number of messages added to queue on given nodes
     *
     * @param queueName  queue name
     * @param containers container list
     * @return total number of messages added in queue on given nodes
     */
    public static long getAddedMessagesCount(String queueName, org.jboss.qa.hornetq.Container... containers) {
        return getAddedMessagesCount(queueName, false, containers);
    }

    /**
     * Returns total number of messages added to topic on given nodes
     *
     * @param destinationName  destination name
     * @param isTopic topic ot queue
     * @param containers container list
     * @return total number of messages added in topic on given nodes
     */
    public static long getAddedMessagesCount(String destinationName ,boolean isTopic, org.jboss.qa.hornetq.Container... containers) {
        long sum = 0;
        for (org.jboss.qa.hornetq.Container container : containers) {
            JMSOperations jmsOperations = container.getJmsOperations();
            long count = -1;
            int numberOfTries = 0;
            int maxNumberOfTries = 10;
            while (count == -1 && numberOfTries < maxNumberOfTries) {
                try {
                    numberOfTries++;
                    count = jmsOperations.getMessagesAdded(destinationName, isTopic);
                    break;
                } catch (Exception ex) {
                    if (numberOfTries > maxNumberOfTries - 1) {
                        throw new RuntimeException("getAddedMessagesCount() failed for destiantion:" + destinationName
                                + " and container: " + container.getName() + ". Number of tries: " + numberOfTries, ex);
                    }
                }
            }
            log.info("Number of messages added on node : " + container.getName() + " is: " + count);
            sum += count;
            jmsOperations.close();
        }
        log.info("Sum of messages added on nodes is: " + sum);
        return sum;
    }

    /**
     * Determine if the given string is a valid IPv4 or IPv6 address.
     *
     * @param ipAddress A string that is to be examined to verify whether or not
     *                  it could be a valid IP address.
     * @return <code>true</code> if the string is a value that is a valid IP
     * address, <code>false</code> otherwise.
     */
    public static boolean isIpv6Address(String ipAddress) {

//        return IPAddressUtil.isIPv6LiteralAddress(ipAddress);
        InetAddress ia;
        try {

            ia = InetAddress.getByName(ipAddress);

        } catch (UnknownHostException e) {

            throw new RuntimeException("Could not determine whether its IPv4 or IPv6 address for:" + ipAddress);
        }

        if (ia instanceof Inet6Address) {
            return true;
        } else {
            return false;
        }
    }

    public static void main(String[] args) {
        String isIpv6Address1 = "2620:52:0:105f::ffff:26";
        String isIpv6Address2 = "::1";
        String isIpv6Address3 = "2620:52:0:105f:0023:dfff:26:2434";
        String isIpv6Address4 = "10.33.22.11";
        String isIpv6Address5 = "localhost";
        System.out.println("isIpv6Address: " + isIpv6Address1 + ":" + isIpv6Address(isIpv6Address1));
        System.out.println("isIpv6Address: " + isIpv6Address2 + ":" + isIpv6Address(isIpv6Address2));
        System.out.println("isIpv6Address: " + isIpv6Address3 + ":" + isIpv6Address(isIpv6Address3));
        System.out.println("isIpv6Address: " + isIpv6Address4 + ":" + isIpv6Address(isIpv6Address4));
        System.out.println("isIpv6Address: " + isIpv6Address5 + ":" + isIpv6Address(isIpv6Address5));
    }

    public static Context getEAP7Context(Container container) throws NamingException {
        return getEAP7Context(container.getHostname(), container.getJNDIPort(), Constants.JNDI_CONTEXT_TYPE.NORMAL_CONTEXT);
    }

    public static Context getEAP7Context(String hostname, int jndiPort) throws NamingException {
        return getEAP7Context(hostname, jndiPort, Constants.JNDI_CONTEXT_TYPE.NORMAL_CONTEXT);
    }

    public static Context getEAP7Context(String hostname, int jndiPort, Constants.JNDI_CONTEXT_TYPE contextType) throws NamingException {
        final Properties env = new Properties();
        env.put(Context.INITIAL_CONTEXT_FACTORY, Constants.INITIAL_CONTEXT_FACTORY_EAP7);
        if (isIpv6Address(hostname)) {
            env.put(Context.PROVIDER_URL, String.format("%s[%s]:%s", Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP7, hostname, jndiPort));
        } else {
            env.put(Context.PROVIDER_URL, String.format("%s%s:%s", Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP7, hostname, jndiPort));
        }
        if (Constants.JNDI_CONTEXT_TYPE.EJB_CONTEXT.equals(contextType)) {
            env.put("jboss.naming.client.ejb.context", true);
        }
        return new InitialContext(env);
    }

    /**
     * Waits for the org.jboss.qa.hornetq.apps.clients to finish. If they do not
     * finish in the specified time out then it fails the test.
     *
     * @param clients org.jboss.qa.hornetq.apps.clients
     */
    public static void waitForClientsToFinish(Clients clients) {
        waitForClientsToFinish(clients, 600000);
    }

    /**
     * Waits for the org.jboss.qa.hornetq.apps.clients to finish. If they do not
     * finish in the specified time out then it fails the test.
     *
     * @param clients org.jboss.qa.hornetq.apps.clients
     * @param timeout timeout
     */
    public static void waitForClientsToFinish(Clients clients, long timeout) {
        long startTime = System.currentTimeMillis();
        try {
            while (!clients.isFinished()) {
                Thread.sleep(1000);
                if (System.currentTimeMillis() - startTime > timeout) {
                    DebugTools.printThreadDump();

                    for (Client c : clients.getConsumers()) {
                        c.forcedStop();
                    }
                    for (Client c : clients.getProducers()) {
                        c.forcedStop();
                    }
                    Assert.fail("Clients did not stop in : " + timeout + "ms. Failing the test and trying to kill them all.");
                }
            }
        } catch (InterruptedException e) {
            log.error("waitForClientsToFinish failed: ", e);
        }
    }

    /**
     *
     * @param clients
     * @return return true if all client threads in the list finished ( = Thread.isAlive() returns false)
     * @throws InterruptedException
     */
    private static boolean isFinished(List<Client> clients) throws InterruptedException {

        boolean isFinished = true;

        for (Thread c : clients) {
            if (c.isAlive()) {
                isFinished = false;
            }
        }
        return isFinished;
    }

    /**
     * Waits for clients in the list to finish. If they do not
     * finish in the specified time out then it fails the test.
     *
     * @param clients list of Client
     * @param timeout timeout
     */
    public static void waitForClientsToFinish(List<Client> clients, long timeout) {

        long startTime = System.currentTimeMillis();
        try {
            while (!isFinished(clients)) {
                Thread.sleep(1000);
                if (System.currentTimeMillis() - startTime > timeout) {
                    DebugTools.printThreadDump();
                    for (Client c : clients) {
                        c.forcedStop();
                    }
                    Assert.fail("Clients did not stop in : " + timeout + "ms. Failing the test and trying to kill them all.");
                }
            }
        } catch (InterruptedException e) {
            log.error("waitForClientsToFinish failed: ", e);
        }
    }

    /**
     * Ping the given port until it's open. This method is used to check whether
     * HQ started on the given port. For example after failover/failback.
     *
     * @param ipAddress ipAddress
     * @param port      port
     * @param timeout   timeout
     */
    public static boolean waitHornetQToAlive(String ipAddress, int port, long timeout) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (!CheckServerAvailableUtils.checkThatServerIsReallyUp(ipAddress, port) && System.currentTimeMillis() - startTime < timeout) {
            Thread.sleep(1000);
        }

        if (!CheckServerAvailableUtils.checkThatServerIsReallyUp(ipAddress, port)) {
            Assert.fail("Server: " + ipAddress + ":" + port + " did not start again. Time out: " + timeout);
        }
        return CheckServerAvailableUtils.checkThatServerIsReallyUp(ipAddress, port);
    }

    /**
     * Method blocks until sum of messages received is equal or greater the
     * numberOfMessages, if timeout expires then Assert.fail
     * <p>
     *
     * @param receivers        receivers
     * @param numberOfMessages numberOfMessages
     * @param timeout          timeout
     */
    public static void waitForAtLeastOneReceiverToConsumeNumberOfMessages(List<Client> receivers, int numberOfMessages, long timeout) {
        long startTimeInMillis = System.currentTimeMillis();

        int sum = 0;

        do {

            sum = 0;

            for (Client c : receivers) {
                sum += c.getCount();
            }

            if ((System.currentTimeMillis() - startTimeInMillis) > timeout) {
                Assert.fail("Clients did not receive " + numberOfMessages + " in timeout: " + timeout);
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        } while (sum <= numberOfMessages);
    }

    /**
     * Returns true if the given number of messages get below the specified expectedNumberOfMessages in queue in the given
     * timeout. Otherwise it returns false.
     *
     * @param container                container
     * @param queueCoreName            queue name
     * @param expectedNumberOfMessages number of messages
     * @param timeout                  timeout
     * @return Returns true if the given number of messages is in queue in the
     * given timeout. Otherwise it returns false.
     * @throws Exception
     */
    public static boolean waitUntilNumberOfMessagesInQueueIsBelow(org.jboss.qa.hornetq.Container container, String queueCoreName,
                                                           int expectedNumberOfMessages, long timeout) throws Exception {

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        long startTime = System.currentTimeMillis();

        while ((jmsAdminOperations.getCountOfMessagesOnQueue(queueCoreName)) > expectedNumberOfMessages
                && System.currentTimeMillis() - startTime < timeout) {
            Thread.sleep(500);
        }
        jmsAdminOperations.close();

        if (System.currentTimeMillis() - startTime > timeout) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * It will check whether messages are still consumed from this queue. It will return after timeout or there is 0 messages
     * in queue.
     *
     * @param queueName
     * @param timeout
     * @param containers
     */
    public static void waitUntilMessagesAreStillConsumed(String queueName, long timeout, Container... containers) throws Exception {
        long startTime = System.currentTimeMillis();
        long lastCount = new JMSTools().countMessages(queueName, containers);
        long newCount = -1;
        while ((newCount = new JMSTools().countMessages(queueName, containers)) > 0) {
            // check there is a change
            // if yes then change lastCount and start time
            // else check time out and if timed out then return
            log.info("last count " + lastCount + ", new count: " + newCount);
            if (lastCount - newCount != 0) {
                lastCount = newCount;
                startTime = System.currentTimeMillis();
                log.info("last count is set to " + lastCount + ", startTime is set to: " + startTime);
            } else if (System.currentTimeMillis() - startTime > timeout) {
                log.info("Time out expired -return. last count is set to " + lastCount + ", new count: " + newCount + ", startTime is set to: " + startTime);
                return;
            }
            Thread.sleep(5000);
        }
    }

    public static Map<String, String> getJndiPropertiesToContainers(Container... containers) throws Exception {
        if (containers[0].getContainerType().equals(Constants.CONTAINER_TYPE.EAP6_CONTAINER)) {
            return getJndiPropertiesToContainersEAP6(containers);
        } else {
            return getJndiPropertiesToContainersEAP7(containers);
        }
    }

    public static Map<String, String> getJndiPropertiesToContainersEAP6(Container... containers) throws Exception {
        Map<String, String> jndiProperties = new HashMap<String, String>();
        jndiProperties.put(Context.INITIAL_CONTEXT_FACTORY, Constants.INITIAL_CONTEXT_FACTORY_EAP6);
        StringBuilder providerUrl = new StringBuilder();

        for (Container container : containers) {
            providerUrl.append(Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP6);
            providerUrl.append(container.getHostname());
            providerUrl.append(":");
            providerUrl.append(container.getJNDIPort());
            providerUrl.append(",");
        }
        providerUrl.deleteCharAt(providerUrl.lastIndexOf(",")); // remove last comma
        jndiProperties.put(Context.PROVIDER_URL, providerUrl.toString());
        return jndiProperties;
    }

    public static Map<String, String> getJndiPropertiesToContainersEAP7(Container... containers) throws Exception {
        Map<String, String> jndiProperties = new HashMap<String, String>();
        jndiProperties.put(Context.INITIAL_CONTEXT_FACTORY, Constants.INITIAL_CONTEXT_FACTORY_EAP7);
        StringBuilder providerUrl = new StringBuilder();

        for (Container container : containers) {
            providerUrl.append(Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP7);
            providerUrl.append(container.getHostname());
            providerUrl.append(":");
            providerUrl.append(container.getJNDIPort());
            providerUrl.append(",");
        }
        providerUrl.deleteCharAt(providerUrl.lastIndexOf(",")); // remove last comma
        jndiProperties.put(Context.PROVIDER_URL, providerUrl.toString());
        return jndiProperties;
    }

    public static boolean isRegisteredInJNDI(Container container, String name) throws Exception {
        Context ctx = null;
        try {
            ctx = container.getContext();
            NamingEnumeration<NameClassPair> list = ctx.list("");

            while (list.hasMore()) {
                if (name.equals(list.next().getName())) {
                    return true;
                }
            }
            return false;
        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }
    }

    public static void closeQuitely(Connection obj) {
        if (obj != null) {
            try {
                obj.close();
            } catch (JMSException e) {
                log.warn(e.getMessage(), e);
            }
        }
    }

    public static void closeQuitely(Context obj) {
        if (obj != null) {
            try {
                obj.close();
            } catch (NamingException e) {
                log.warn(e.getMessage(), e);
            }
        }
    }
}
