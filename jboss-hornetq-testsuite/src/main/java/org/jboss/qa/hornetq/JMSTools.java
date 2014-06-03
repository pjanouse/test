package org.jboss.qa.hornetq;

import sun.net.util.IPAddressUtil;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Utilities for JMS clients
 */
public final class JMSTools {

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
                // Ignore it
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (JMSException e) {
                // Ignore it
            }
        }
        if (context != null) {
            try {
                context.close();
            } catch (NamingException e) {
                // Ignore it
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

        return IPAddressUtil.isIPv6LiteralAddress(ipAddress);
    }

    public static void main(String[] args)  {
        String isIpv6Address1 = "2620:52:0:105f::ffff:26";
        String isIpv6Address2 = "::1";
        String isIpv6Address3 = "2620:52:0:105f:0023:dfff:26:2434";
        String isIpv6Address4 = "10.33.22.11";
        System.out.println("isIpv6Address: " + isIpv6Address1 + ":" + isIpv6Address(isIpv6Address1));
        System.out.println("isIpv6Address: " + isIpv6Address2 + ":" + isIpv6Address(isIpv6Address2));
        System.out.println("isIpv6Address: " + isIpv6Address3 + ":" + isIpv6Address(isIpv6Address3));
        System.out.println("isIpv6Address: " + isIpv6Address4 + ":" + isIpv6Address(isIpv6Address4));
    }

}
