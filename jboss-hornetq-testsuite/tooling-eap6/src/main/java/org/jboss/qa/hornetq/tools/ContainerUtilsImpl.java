package org.jboss.qa.hornetq.tools;

import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.qa.hornetq.HornetQTestCaseConstants;

import java.util.HashMap;
import java.util.Map;
import org.kohsuke.MetaInfServices;


/**
 *
 * Contains methods related to Arquillian node/container defined in arquillian.xml for EAP 6.
 *
 * @author mnovak@redhat.com
 */
@MetaInfServices
public class ContainerUtilsImpl extends ContainerUtils {

    Map<String,ContainerInfo> containerInfosMap = new HashMap<String, ContainerInfo>();

    ArquillianDescriptor arquillianDescriptor;

    public static ContainerUtilsImpl INSTANCE;

    public ContainerUtilsImpl() {

    }

    private ContainerUtilsImpl(ArquillianDescriptor arquillianDescriptor, ContainerInfo... containerInfos) {

        this.arquillianDescriptor = arquillianDescriptor;

        for (ContainerInfo containerInfo : containerInfos)  {
            containerInfosMap.put(containerInfo.getName(), containerInfo);
        }
    }

    public synchronized ContainerUtils getInstance(ArquillianDescriptor arquillianDescriptor, ContainerInfo... containerInfos)  {
        if (INSTANCE == null) {

            INSTANCE = new ContainerUtilsImpl(arquillianDescriptor, containerInfos);

        }

        return INSTANCE;
    }

    @Override
    public int getPort() {
        for (GroupDef groupDef : arquillianDescriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                if (containerDef.getContainerProperties().containsKey("managementPort")) {
                    return Integer.valueOf(containerDef.getContainerProperties().get("managementPort"));
                }
            }
        }

        return 9999;
    }

    @Override
    public int getPort(String containerName) {
        for (GroupDef groupDef : arquillianDescriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                if (containerDef.getContainerName().equalsIgnoreCase(containerName)) {
                    if (containerDef.getContainerProperties().containsKey("managementPort")) {
                        return Integer.valueOf(containerDef.getContainerProperties().get("managementPort"));
                    }
                }
            }
        }

        return 9999;
    }

    @Override
    public int getJNDIPort() {
        if (getFirstContainerInfo().getContainerType() == HornetQTestCaseConstants.CONTAINER_TYPE.EAP5_CONTAINER
                || getFirstContainerInfo().getContainerType() == HornetQTestCaseConstants.CONTAINER_TYPE.EAP5_WITH_JBM_CONTAINER
                || getFirstContainerInfo().getContainerType() == HornetQTestCaseConstants.CONTAINER_TYPE.EAP6_LEGACY_CONTAINER) {
            return 1099 + getFirstContainerInfo().getPortOffset();
        } else {
            return 4447 + getFirstContainerInfo().getPortOffset();
        }
    }

    @Override
    public int getJNDIPort(String containerName) {
        if (getContainerInfo(containerName).getContainerType() == HornetQTestCaseConstants.CONTAINER_TYPE.EAP5_CONTAINER
                || getContainerInfo(containerName).getContainerType() == HornetQTestCaseConstants.CONTAINER_TYPE.EAP5_WITH_JBM_CONTAINER
                || getContainerInfo(containerName).getContainerType() == HornetQTestCaseConstants.CONTAINER_TYPE.EAP6_LEGACY_CONTAINER) {
            return 1099 + getContainerInfo(containerName).getPortOffset();
        } else {
            return 4447 + getContainerInfo(containerName).getPortOffset();
        }
    }

    @Override
    public int getLegacyJNDIPort(String containerName) {

        return 1099 + getContainerInfo(containerName).getPortOffset();

    }

    @Override
    public String getHostname(String containerName) {

        return getContainerInfo(containerName).getIpAddress();

    }

    @Override
    public int getHornetqBackupPort(String containerName) {

        return HornetQTestCaseConstants.PORT_HORNETQ_BACKUP_DEFAULT_EAP6 + getContainerInfo(containerName).getPortOffset();

    }

    @Override
    public int getHornetqPort(String containerName) {

        return HornetQTestCaseConstants.PORT_HORNETQ_DEFAULT_EAP6 + getContainerInfo(containerName).getPortOffset();

    }

    @Override
    public String getJbossHome(String containerName) {

        return getContainerInfo(containerName).getJbossHome();

    }

    @Override
    public int getBytemanPort(String containerName) {

        return getContainerInfo(containerName).getBytemanPort();

    }

    @Override
    public HornetQTestCaseConstants.CONTAINER_TYPE getContainerType(String containerName) {

        return getContainerInfo(containerName).getContainerType();

    }

    @Override
    public ContainerInfo getContainerInfo(String containerName) {

        ContainerInfo containerInfo = containerInfosMap.get(containerName);

        if (containerInfo == null)  {
            throw new RuntimeException(String.format("Name of the container %s is not known. It can't be used", containerName));
        }

        return containerInfo;
    }

    /**
     * Returns first container info for first node defined in arquillian.xml.
     *
     * @return ContainerInfo of first container in arquillian.xml
     */
    private ContainerInfo getFirstContainerInfo()   {

        ContainerDef containerDef = arquillianDescriptor.getGroups().get(0).getGroupContainers().get(0);

        return  containerInfosMap.get(containerDef.getContainerName());

    }
}
