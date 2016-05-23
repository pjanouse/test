package org.jboss.qa.hornetq.test.security;


import org.jboss.crypto.CryptoUtil;
import org.jboss.qa.hornetq.Container;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;


/**
 * Builder class for creating application-(users|roles).properties files in test server directory.
 *
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public class UsersSettings {

    private UsersSettings() {
    }


    public static Builder forDefaultEapServer() {
        return new Builder(System.getProperty("JBOSS_HOME_1"));
    }

    /**
     * @param jbossHome path to home directory of server, usually System.getProperty("JBOSS_HOME_X")
     * @deprecated use cleaner version with Container, UsersSettings.forEapServer(final Container container);
     */
    @Deprecated
    public static Builder forEapServer(final String jbossHome) {
        return new Builder(jbossHome);
    }

    public static Builder forEapServer(final Container container) {
        return new Builder(container.getServerHome());
    }

    public static class Builder {

        private static final String CONFIG_DIRECTORY = "standalone" + File.separator + "configuration";

        private static final String ROLES_FILE = "application-roles.properties";

        private static final String USERS_FILE = "application-users.properties";

        private final String serverHome;

        private final Map<String, EapUser> users = new HashMap<String, EapUser>();


        private Builder(final String serverHome) {
            this.serverHome = serverHome;

            // create guest account by default
            this.users.put("guest", new EapUser("guest", null, "guest"));
        }


        public Builder withUser(final String userName, final String plaintextPassword,
                final Collection<? extends String> roles) {

            this.users.put(userName, new EapUser(userName, plaintextPassword, roles));
            return this;
        }


        public Builder withUser(final String userName, final String plaintextPassword, final String... roles) {
            return this.withUser(userName, plaintextPassword, Arrays.asList(roles));
        }


        public void create() throws IOException {
            this.createRolesFile();
            this.createUsersFile();
        }


        private void createRolesFile() throws IOException {
            FileWriter fw = null;
            try {
                fw = new FileWriter(this.getConfigurationDirectoryPath() + ROLES_FILE);
                for (EapUser user : this.users.values()) {
                    fw.append(user.getUserName()).append("=").append(user.getRolesString());
                    fw.append("\n");
                }
            } finally {
                if (fw != null) {
                    fw.close();
                }
            }
        }


        private void createUsersFile() throws IOException {
            FileWriter fw = null;
            try {
                fw = new FileWriter(this.getConfigurationDirectoryPath() + USERS_FILE);
                for (EapUser user : this.users.values()) {
                    if (user.getPlaintextPassword() != null && !user.getPlaintextPassword().isEmpty()) {
                        fw.append(user.getUserName()).append("=").append(user.getEncryptedPassword());
                        fw.append("\n");
                    }
                }
            } finally {
                if (fw != null) {
                    fw.close();
                }
            }
        }


        private String getConfigurationDirectoryPath() {
            return this.serverHome + File.separator + CONFIG_DIRECTORY + File.separator;
        }

    }


    private static class EapUser {

        private static final String HASH_ALGORITHM = "MD5";

        private static final String HASH_ENCODING = "hex";

        private static final String SECURITY_REALM = "ApplicationRealm";

        private final String userName;

        private final String plaintextPassword;

        private final Set<String> roles;


        public EapUser(final String userName, final String plaintextPassword,
                final Collection<? extends String> roles) {

            if (userName == null || userName.isEmpty()) {
                throw new IllegalArgumentException("User name cannot be empty");
            }

            this.userName = userName;
            this.plaintextPassword = plaintextPassword;

            if (roles == null) {
                this.roles = new HashSet<String>();
            } else {
                this.roles = new HashSet<String>(roles);
            }
        }


        public EapUser(final String userName, final String plaintextPassword, final String... roles) {
            this(userName, plaintextPassword, Arrays.asList(roles));
        }


        public String getUserName() {
            return this.userName;
        }


        public String getPlaintextPassword() {
            return this.plaintextPassword;
        }


        public String getEncryptedPassword() {
            return CryptoUtil.createPasswordHash(HASH_ALGORITHM, HASH_ENCODING, null, null,
                    this.userName + ":" + SECURITY_REALM + ":" + this.plaintextPassword);
        }


        public Set<String> getRoles() {
            return Collections.unmodifiableSet(this.roles);
        }


        public String getRolesString() {
            if (this.roles.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            for (String r : this.roles) {
                sb.append(r).append(",");
            }
            sb.delete(sb.lastIndexOf(","), sb.length());
            return sb.toString();
        }

    }

}
