package org.jboss.qa.hornetq.tools;

import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.qa.hornetq.HornetQTestCaseConstants;

/**
 * @author mnovak@redhat.com
 */
public abstract class ContainerUtils {

    public static ContainerUtils INSTANCE;

    /**
     * This must be called first so new Instance is initialized.
     *
     * Note:
     * Implementation of this method should be synchronized, just for sure.
     *
     * @param arquillianDescriptor arquillian descriptor
     * @param containerInfos container info instances (usually 4 instances will be passed)
     *
     * @return ContainerUtils
     */
    public abstract ContainerUtils getInstance(ArquillianDescriptor arquillianDescriptor, ContainerInfo... containerInfos);

    /**
     * Returns already created instance of implementation of this class.
     *
     * It will throw RuntimeException if instance was not initialized.
     *
     * @return ContainerUtils instance
     *
     */
    public static synchronized ContainerUtils getInstance() {
        if (INSTANCE == null)   {

            throw new RuntimeException("Container utils are not initialized. This must be done by HornetQTestCase" +
                    " instance by calling getInstance(ContainerInfo... containerInfos) on ContainerUtils instance.");

        }

        return INSTANCE;
    }

    /**
     * Returns management port of fist container defined in arquillian.xml.
     *
     * @return
     */
    public abstract int getPort();

    public abstract int getPort(String containerName);


    /**
     * Returns port for JNDI port of fist container defined in arquillian.xml.
     *
     * @return returns port for JNDI service of 1st container in arquillian.xml
     */
    public abstract int getJNDIPort();

    /**
     * Returns port for JNDI service
     *
     * @param containerName name of the container in arquillian.xml (must be same as for CONTAINERX in @HornetQTestCaseConstants
     *
     * @return returns default port for JNDI service
     */
    public abstract int getJNDIPort(String containerName);

    public abstract int getLegacyJNDIPort(String containerName);

    public abstract String getHostname(String containerName);

    public abstract int getHornetqBackupPort(String containerName);

    public abstract int getHornetqPort(String containerName);

    public abstract String getJbossHome(String containerName);

    public abstract int getBytemanPort(String containerName);

    public abstract HornetQTestCaseConstants.CONTAINER_TYPE getContainerType(String containerName);

    public abstract ContainerInfo getContainerInfo(String containerName);

}

