package org.jboss.qa.hornetq.tools.jms.settings;


/**
 * @author <a href="mailto:msvehla@redhat.com">Martin Svehla</a>
 */
public class BroadcastingSettings {

    private static final String DEFAULT_GROUP_NAME = "bc-group";

    private String connectorName;

    private String backupConnectorName;

    private String bindingAddress;

    private Integer bindingPort;

    private String socketBindingName;

    private String multicastAddress;

    private Integer multicastPort;

    private String broadcastGroupName;

    private Integer broadcastPeriod;

    private String jgroupsStack;

    private String jgroupsChannel;


    private BroadcastingSettings(final String name) {
        this.broadcastGroupName = name;
    }


    private boolean isValid() {
        return this.isJgroupDefined() || this.isBindingConnectionDefined() || this.isSockedBindingDefined();
    }


    private boolean isJgroupDefined() {
        return this.jgroupsStack != null && !this.jgroupsStack.isEmpty()
                && this.jgroupsChannel != null && !this.jgroupsChannel.isEmpty();
    }


    private boolean isBindingConnectionDefined() {
        return this.bindingAddress != null && !this.bindingAddress.isEmpty()
                && this.bindingPort != null && this.bindingPort > 0;
    }


    private boolean isSockedBindingDefined() {
        return this.socketBindingName != null && !this.socketBindingName.isEmpty();
    }


    public static BroadcastSettingsBuilder forGroup(final String broadcastGroupName) {
        return new BroadcastSettingsBuilder(broadcastGroupName);
    }


    public static BroadcastSettingsBuilder forDefaultGroup() {
        return new BroadcastSettingsBuilder(DEFAULT_GROUP_NAME);
    }


    public static final class BroadcastSettingsBuilder {

        private BroadcastingSettings settings;


        private BroadcastSettingsBuilder(final String name) {
            this.settings = new BroadcastingSettings(name);
        }


        public BroadcastingSettings create() {
            if (!this.settings.isValid()) {
                throw new IllegalStateException("Insufficient informations to create broadcast group settings");
            }

            return this.settings;
        }

    }

}
