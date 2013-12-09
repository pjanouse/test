package org.jboss.qa.hornetq.tools.jms.settings;


import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.qa.hornetq.tools.HornetQAdminOperationsEAP6;
import org.jboss.qa.hornetq.tools.JMSOperations;


/**
 *
 * @author <a href="mailto:msvehla@redhat.com">Martin Svehla</a>
 */
public final class JmsServerSettings {

    private JmsServerSettings() {
    }


    public static SettingsBuilder fromOperations(final JMSOperations operations) {
        if (operations instanceof HornetQAdminOperationsEAP6) {
            return new Eap6SettingsBuilder(operations);
        } else {
            throw new IllegalArgumentException("Unknown JMS operations implementation "
                    + operations.getClass().getName());
        }
    }


    public static SettingsBuilder forContainer(final ContainerType type, final String node,
            final ArquillianDescriptor arquillianDescriptor) {

        if (type == null) {
            throw new IllegalArgumentException("Container type must be defined");
        }

        switch (type) {
            case EAP6_WITH_HORNETQ:
                return new Eap6SettingsBuilder(node, arquillianDescriptor);
            default:
                throw new IllegalArgumentException("Unknown container type " + type.toString());
        }
    }


    public static enum ContainerType {

        EAP6_WITH_HORNETQ

    }

}
