package org.jboss.qa.hornetq;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.tools.ContainerInfo;
import org.jboss.qa.hornetq.tools.JMSOperations;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Utilities for JMS org.jboss.qa.hornetq.apps.clients
 */
public final class JMSTools {

    private static final Logger log = Logger.getLogger(JMSTools.class);

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
    public static Context getEAP6Context(String hostName, int port) throws NamingException {
        final Properties env = new Properties();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
        env.put(Context.PROVIDER_URL, String.format("remote://%s:%s", hostName, port));
        return new InitialContext(env);
    }

    /**
     * Waits until all containers in the given queue contains the given number of messages
     *
     * @param queueName        queue name
     * @param numberOfMessages number of messages
     * @param timeout          time out
     * @param containers       container list
     * @return returns true if there is numberOfMessages in queue, when timeout expires it returns false
     * @throws Exception
     */
    public boolean waitForMessages(String queueName, long numberOfMessages, long timeout, org.jboss.qa.hornetq.Container... containers) throws Exception {

        long startTime = System.currentTimeMillis();

        long count = 0;
        while ((count = countMessages(queueName, containers)) < numberOfMessages) {
            List<String> containerNames = new ArrayList<String>(containers.length);
            for (org.jboss.qa.hornetq.Container c: containers) {
                containerNames.add(c.getName());
            }

            log.info("Total number of messages in queue: " + queueName + " on node "
                    + Arrays.toString(containerNames.toArray()) + " is " + count);
            Thread.sleep(1000);
            if (System.currentTimeMillis() - startTime > timeout) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns total number of messages in queue on given nodes
     *
     * @param queueName      queue name
     * @param containers     container list
     * @return total number of messages in queue on given nodes
     */
    public long countMessages(String queueName, org.jboss.qa.hornetq.Container... containers) {
        long sum = 0;
        for (org.jboss.qa.hornetq.Container container : containers) {
            JMSOperations jmsOperations = container.getJmsOperations();
            long count = jmsOperations.getCountOfMessagesOnQueue(queueName);
            log.info("Number of messages on node : " + container + " is: " + count);
            sum += count;
            jmsOperations.close();
        }
        return sum;
    }


    /**
     * Returns EAP 5 context
     *
     * @param hostName host name
     * @param port     target port with the service
     * @return instance of the context
     * @throws NamingException if something goes wrong
     */
    public static Context getEAP5Context(String hostName, int port) throws NamingException {
        Properties properties = new Properties();
        properties.setProperty("java.naming.factory.initial", "org.jnp.interfaces.NamingContextFactory");
        properties.setProperty("java.naming.provider.url", "jnp://" + hostName + ":" + port);
        properties.setProperty("java.naming.factory.url.pkgs", "org.jnp.interfaces.NamingContextFactory");
        return new InitialContext(properties);
    }



    /**
     * Determine if the given string is a valid IPv4 or IPv6 address.
     *
     * @param ipAddress A string that is to be examined to verify whether or not
     *  it could be a valid IP address.
     * @return <code>true</code> if the string is a value that is a valid IP address,
     *  <code>false</code> otherwise.
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

    public static void main(String[] args)  {
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

    public static Context getEAP7Context(String hostname, int jndiPort) throws NamingException {
        final Properties env = new Properties();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
        env.put(Context.PROVIDER_URL, String.format("remote://%s:%s", hostname, jndiPort));
        return new InitialContext(env);
    }
}
