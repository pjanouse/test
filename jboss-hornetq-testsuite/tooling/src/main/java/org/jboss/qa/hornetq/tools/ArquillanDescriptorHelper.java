package org.jboss.qa.hornetq.tools;


import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;


/**
 * Helper methods to programmatically getting informations from arquillian descriptor used in test.
 *
 * @author <a href="mailto:msvehla@redhat.com">Martin Svehla</a>
 */
public final class ArquillanDescriptorHelper {

    private static final String BIND_ADDRESS_KEY = "bindAddress";

    private static final String MANAGEMENT_ADDRESS_KEY = "managementAddress";

    private static final String MANAGEMENT_PORT_KEY = "managementPort";


    private ArquillanDescriptorHelper() {
    }


    public static boolean isDefaultContainer(final String containerName,
            final ArquillianDescriptor arquillianDescriptor) {

        ContainerDef container = ArquillanDescriptorHelper.findContainer(containerName, arquillianDescriptor);
        return container != null && container.isDefault();

    }


    public static String getContainerHostname(final String containerName,
            final ArquillianDescriptor arquillianDescriptor) {

        ContainerDef container = ArquillanDescriptorHelper.findContainer(containerName, arquillianDescriptor);

        if (container != null && container.getContainerProperties().containsKey(BIND_ADDRESS_KEY)) {
            return container.getContainerProperties().get(BIND_ADDRESS_KEY);
        }

        if (container != null && container.getContainerProperties().containsKey(MANAGEMENT_ADDRESS_KEY)) {
            return container.getContainerProperties().get(MANAGEMENT_ADDRESS_KEY);
        }

        return "localhost";
    }


    public static int getContainerPort(final String containerName, final ArquillianDescriptor arquillianDescriptor) {
        ContainerDef container = ArquillanDescriptorHelper.findContainer(containerName, arquillianDescriptor);

        if (container != null && container.getContainerProperties().containsKey(MANAGEMENT_PORT_KEY)) {
            return Integer.valueOf(container.getContainerProperties().get(MANAGEMENT_PORT_KEY));
        } else {
            return 9999;
        }
    }


    public static ContainerDef findContainer(final String containerName,
            final ArquillianDescriptor arquillianDescriptor) {

        for (GroupDef group : arquillianDescriptor.getGroups()) {
            for (ContainerDef container : group.getGroupContainers()) {
                if (container.getContainerName().equalsIgnoreCase(containerName)) {
                    return container;
                }
            }
        }

        return null;
    }

}
