package org.jboss.qa.hornetq.tools;


import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.HornetQTestCaseConstants;

import java.io.File;

/**
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
@Deprecated
public final class ContainerInfo {

    // Logger
    private static final Logger log = Logger.getLogger(ContainerInfo.class);

    private final String name;

    // name of the node in case of domain mode, useful for setting up ModelNode paths, that require this for getting
    // runtime info
    private final String domainName;

    private final String ipAddress;

    private final int bytemanPort;

    private final int portOffset;

    private final String jbossHome;

    private HornetQTestCaseConstants.CONTAINER_TYPE containerType;

    public ContainerInfo(final String name, final String domainName, final String ipAddress, final int bytemanPort,
                         final int portOffset, final String jbossHome) {

        this.name = name;
        this.domainName = domainName;
        this.ipAddress = ipAddress;
        this.bytemanPort = bytemanPort;
        this.portOffset = portOffset;
        this.jbossHome = jbossHome;
        setContainerType(jbossHome);

        log.debug("Creating container info instance: " + "Container: " + name + ", jbossHome: " + jbossHome
                + " , ipAddress: " + ipAddress + ", bytemanPort: " + bytemanPort + ", portOffset: " + portOffset
                + " container type: " + containerType);
    }

    private void setContainerType(String jbossHome)  {
        StringBuilder eap5hqPath = new StringBuilder(jbossHome);
        eap5hqPath.append(File.separator).append("client").append(File.separator).append("hornetq-core-client.jar");
        StringBuilder eap5jbmPath = new StringBuilder(jbossHome);
        eap5jbmPath.append(File.separator).append("client").append(File.separator).append("jboss-messaging-client.jar");

        String arqConfigurationFile = System.getProperty("arquillian.xml");
        if (new File(eap5hqPath.toString()).exists())   {
            containerType = HornetQTestCaseConstants.CONTAINER_TYPE.EAP5_CONTAINER;
        } else if (new File(eap5jbmPath.toString()).exists())   {
            containerType = HornetQTestCaseConstants.CONTAINER_TYPE.EAP5_WITH_JBM_CONTAINER;
        } else if (arqConfigurationFile != null && !arqConfigurationFile.trim().isEmpty()
                && arqConfigurationFile.toLowerCase().contains("eap6-legacy")) {

            containerType = HornetQTestCaseConstants.CONTAINER_TYPE.EAP6_LEGACY_CONTAINER;
        } else if (arqConfigurationFile != null && !arqConfigurationFile.trim().isEmpty()
                && arqConfigurationFile.toLowerCase().contains("domain")) {

            containerType = HornetQTestCaseConstants.CONTAINER_TYPE.EAP6_DOMAIN_CONTAINER;
        } else {
            containerType = HornetQTestCaseConstants.CONTAINER_TYPE.EAP6_CONTAINER;
        }
    }

    public String getName() {
        return name;
    }


    public String getDomainName() {
        return domainName;
    }


    public String getIpAddress() {
        return ipAddress;
    }


    public int getBytemanPort() {
        return bytemanPort;
    }

    /**
     *  This should be set in pom.xml.
     */
    public int getPortOffset()  {
        return portOffset;
    }

    public String getJbossHome()    {
        return jbossHome;
    }

    public HornetQTestCaseConstants.CONTAINER_TYPE getContainerType()   {
        return containerType;
    }

    public String toString()    {
        return "Container: " + name + " , ipAddress: " + ipAddress + ", bytemanPort: " + bytemanPort + ", portOffset: " + portOffset;
    }

}
