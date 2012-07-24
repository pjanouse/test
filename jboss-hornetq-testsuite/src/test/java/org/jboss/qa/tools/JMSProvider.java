package org.jboss.qa.tools;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.apache.log4j.Logger;

/**
 * Utility class for getting implementations of JMSOperations interface
 *
 * @author mnovak@redhat.com
 */
public class JMSProvider {

    private static final String PROPERTY_NAME = "JMS_PROVIDER_CLASS";
    private static final String FILE_NAME = "provider.properties";
    private static final Logger logger = Logger.getLogger(JMSProvider.class);

    /**
     * Gets an instance of an JMSOperations implementation for a particular JMS
     * provider based on the class name given by property
     * "jmsoperations.implementation.class" in jmsoperations.properties
     * somewhere on the class path The property should contain a fully qualified
     * name of a class that implements JMSOperations interface The setting in
     * that file can be overridden by a system property declaration.
     *
     * @return a JMSOperations implementation that is JMS-provider-dependent
     */
    public static JMSOperations getInstance(String containerName) {
        
        if (containerName == null || "".equals(containerName))   {
            throw new IllegalStateException("ContainerName cannot be null or empty.");
        }
        
        String className;
        // first try to get the property from system properties
        className = System.getProperty(PROPERTY_NAME);
        // if this was not defined, try to get it from jmsoperations.properties
        if (className == null) {
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            InputStream stream = tccl.getResourceAsStream(FILE_NAME);
            Properties propsFromFile = new Properties();
            try {
                propsFromFile.load(stream);
                className = propsFromFile.getProperty(PROPERTY_NAME);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        if (className == null) {
            throw new IllegalArgumentException("Please specify a property " + PROPERTY_NAME + " in " + FILE_NAME);
        }
        logger.info("Creating instance of class: " + className);
        Object jmsOperationsInstance = null;
        try {
            Class clazz = Class.forName(className);
            jmsOperationsInstance = clazz.getConstructor().newInstance();

        } catch (Exception ex) {
            logger.error("Problem during creating jms admin provider class: ", ex);
        }
        if (!(jmsOperationsInstance instanceof JMSOperations)) {
            throw new RuntimeException("Class " + className + " does not implement interface JMSOperations");
        }
        
        // Set provider class specific properties for EAP5 - HornetQAdminOperationsEAP5.java
        if (jmsOperationsInstance instanceof HornetQAdminOperationsEAP5) {
            
            ((HornetQAdminOperationsEAP5) jmsOperationsInstance).setJbossHome(ConfigurationLoader.getJbossHome(containerName));
            ((HornetQAdminOperationsEAP5) jmsOperationsInstance).setProfile(ConfigurationLoader.getProfile(containerName));
            ((HornetQAdminOperationsEAP5) jmsOperationsInstance).setHostname(ConfigurationLoader.getHostname(containerName));
            ((HornetQAdminOperationsEAP5) jmsOperationsInstance).setRmiPort(ConfigurationLoader.getRmiPort(containerName));
            
        }
        
        // Set provider class specific properties for EAP6 - HornetQAdminOperationsEAP6.java
        if (jmsOperationsInstance instanceof HornetQAdminOperationsEAP6) {
            
            ((HornetQAdminOperationsEAP6) jmsOperationsInstance).setHostname(ConfigurationLoader.getHostname(containerName));
            ((HornetQAdminOperationsEAP6) jmsOperationsInstance).setPort(ConfigurationLoader.getPort(containerName));
            ((HornetQAdminOperationsEAP6) jmsOperationsInstance).connect();
            
        }
        
        return (JMSOperations) jmsOperationsInstance;
    }
}