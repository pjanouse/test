package org.jboss.qa.hornetq.test.security;


import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.hornetq.test.HornetQTestCaseConstants;
import org.jboss.qa.tools.JMSOperations;


/**
 * Builder for creating security settings for given queue address mask.
 *
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public class AddressSecuritySettings {

    private AddressSecuritySettings() {
    }


    public static Builder forDefaultContainer(final HornetQTestCase testCase) {
        return forContainer(testCase, HornetQTestCaseConstants.CONTAINER1);
    }


    public static Builder forContainer(final HornetQTestCase testCase, final String containerName) {
        return new Builder(testCase.getJMSOperations(containerName));
    }


    public static class Builder {

        private static final Logger LOG = Logger.getLogger(AddressSecuritySettings.class);

        private final JMSOperations ops;

        private String address;

        private String hornetqServer = "default";

        private final Map<PermissionGroup, Set<String>> permissions =
                new EnumMap<PermissionGroup, Set<String>>(PermissionGroup.class);


        private Builder(final JMSOperations jmsOperations) {
            LOG.debug("Creating new address settings builder");
            this.ops = jmsOperations;

            for (PermissionGroup permissionGroup : PermissionGroup.values()) {
                this.permissions.put(permissionGroup, new HashSet<String>());
            }
        }


        public Builder forAddress(final String addressMask) {
            LOG.debug("Creating security settings for address " + addressMask);
            this.address = addressMask;
            return this;
        }


        public Builder forServer(final String hornetqServer) {
            LOG.debug("Security settings will be created for HornetQ Instance " + hornetqServer);
            this.hornetqServer = hornetqServer;
            return this;
        }


        public Builder addUserPermission(final PermissionGroup group, final String user) {
            LOG.debug(String.format("Adding user %s for permission group %s (address %s)",
                    user, group.name(), String.valueOf(this.address)));
            this.permissions.get(group).add(user);
            return this;
        }


        public Builder giveUserAllPermissions(final String user) {
            LOG.debug(String.format("Giving all permissions to user %s (address %s)",
                    user, String.valueOf(this.address)));
            for (PermissionGroup group : PermissionGroup.values()) {
                this.permissions.get(group).add(user);
            }
            return this;
        }


        public Builder givePermissionToUsers(final PermissionGroup group, final String... users) {
            return this.givePermissionToUsers(group, Arrays.asList(users));
        }


        public Builder givePermissionToUsers(final PermissionGroup group,
                final Collection<? extends String> users) {
            LOG.debug(String.format("Setting following users for permission group %s, previous settings for the group "
                    + "were deleted (address %s):", group.name(), String.valueOf(this.address)));
            this.permissions.get(group).clear();
            for (String u : users) {
                LOG.debug("  " + u);
                this.permissions.get(group).addAll(users);
            }
            return this;
        }


        public void create() {
            if (this.address == null || this.address.isEmpty()) {
                throw new IllegalStateException("Security settings address mask cannot be empty");
            }

            LOG.debug("Writing security settings to the server");
            this.createAddressSettings();
            this.createRoles(this.userList());
            this.createPermissionsSettings();
            this.ops.close();
        }


        private void createAddressSettings() {
            try {
                // try to remove existing settings for the address if there were any
                // NOTE: server won't delete settings for '#' address
                this.ops.removeSecuritySettings(this.hornetqServer, this.address);
            } catch (RuntimeException ignored) {
            }

            if (!"#".equals(this.address)) {
                // security settings for '#' cannot be removed, thus trying to create them again would result
                // in duplicate resource error
                this.ops.addSecuritySetting(this.hornetqServer, this.address);
            } else {
                // if the test supplied '#' definitions, remove guest from all permissions to make sure
                // to get only settings specified by the test
                for (PermissionGroup group : PermissionGroup.values()) {
                    this.ops.setPermissionToRoleToSecuritySettings(this.hornetqServer, this.address,
                            "guest", group.getKey(), false);
                }
            }
        }


        private Set<String> userList() {
            Set<String> users = new HashSet<String>();
            for (Set<String> groupUsers : this.permissions.values()) {
                users.addAll(groupUsers);
            }
            return users;
        }


        private void createRoles(final Collection<? extends String> users) {
            for (String user : users) {
                try {
                    this.ops.addRoleToSecuritySettings(this.address, user);
                } catch (RuntimeException e) {
                    // role is probably set up already
                }
            }
        }


        private void createPermissionsSettings() {
            for (PermissionGroup permissionGroup : PermissionGroup.values()) {
                for (String user : this.permissions.get(permissionGroup)) {
                    this.ops.setPermissionToRoleToSecuritySettings(this.hornetqServer, this.address,
                            user, permissionGroup.getKey(), true);
                }
            }
        }

    }

}
