package org.jboss.qa.hornetq.test.security;


import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import category.Functional;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.IOException;


/**
 * Tests for security settings for various address filters.
 *
 * See {@link PermissionSecurityTestCase} for basic access tests. This test case is about
 * various address mask settings.
 *
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
@RunWith(Arquillian.class)
@Category(Functional.class)
public class FiltersSecurityTestCase extends HornetQTestCase {

    private static final String TEST_ADDRESS = "jms.queue.test.#";

    private static final String TEST_QUEUE_ADDRESS = "test.queue";

    private static final String TEST_QUEUE_JNDI = "jms/test/queue";

    @Before
    @After
    public void stopTestContainer() {
        container(1).stop();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testHashMask() throws Exception {
        this.prepareServer(container(1));
        container(1).start();
        AddressSecuritySettings.forDefaultContainer(this)
                .forAddress("#")
                .giveUserAllPermissions(User.ADMIN.getUserName())
                .create();

        AddressSecuritySettings.forDefaultContainer(this)
                .forAddress("jms.queue.test.#")
                .giveUserAllPermissions(User.USER.getUserName())
                .create();

        container(1).restart();

        this.sendTestMessagesAsUser();
    }


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testStarAsLastMask() throws Exception {
        this.prepareServer(container(1));
        container(1).start();
        AddressSecuritySettings.forDefaultContainer(this)
                .forAddress("#")
                .giveUserAllPermissions(User.ADMIN.getUserName())
                .create();

        AddressSecuritySettings.forDefaultContainer(this)
                .forAddress("jms.queue.test.*")
                .giveUserAllPermissions(User.USER.getUserName())
                .create();

        container(1).restart();

        this.sendTestMessagesAsUser();
    }


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testStarInMask() throws Exception {
        this.prepareServer(container(1));
        container(1).start();
        AddressSecuritySettings.forDefaultContainer(this)
                .forAddress("#")
                .giveUserAllPermissions(User.ADMIN.getUserName())
                .create();

        AddressSecuritySettings.forDefaultContainer(this)
                .forAddress("jms.queue.*.queue")
                .giveUserAllPermissions(User.USER.getUserName())
                .create();

        container(1).restart();

        this.sendTestMessagesAsUser();
    }


    /**
     * Test that more specific settings apply to queue access, despite being later in config file.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testMoreSpecificFilterSettings() throws Exception {
        this.prepareServer(container(1));
        container(1).start();
        AddressSecuritySettings.forDefaultContainer(this)
                .forAddress("#")
                .giveUserAllPermissions(User.ADMIN.getUserName())
                .create();

        AddressSecuritySettings.forDefaultContainer(this)
                .forAddress("jms.queue.#")
                .giveUserAllPermissions(User.ADMIN.getUserName())
                .create();

        AddressSecuritySettings.forDefaultContainer(this)
                .forAddress("jms.queue.test.#")
                .giveUserAllPermissions(User.USER.getUserName())
                .create();

        container(1).restart();

        this.sendTestMessagesAsUser();
    }


    private void sendTestMessagesAsUser() throws Exception {
        SecurityClient client = null;
        try {
            client = new SecurityClient(container(1), TEST_QUEUE_JNDI, 10,
                    User.USER.getUserName(), User.USER.getPassword());
            client.initializeClient();
            client.sendAndReceive();
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }


    private void prepareServer(Container container) throws IOException {

        container.start();
        JMSOperations ops = container(1).getJmsOperations();
        ops.setBindingsDirectory(JOURNAL_DIRECTORY_A);
        ops.setPagingDirectory(JOURNAL_DIRECTORY_A);
        ops.setJournalDirectory(JOURNAL_DIRECTORY_A);
        ops.setLargeMessagesDirectory(JOURNAL_DIRECTORY_A);

        ops.setJournalType("NIO");
        ops.setPersistenceEnabled(true);

        ops.setSecurityEnabled(true);

        ops.removeAddressSettings("#");
        ops.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

        ops.close();

        UsersSettings.forDefaultEapServer()
                .withUser(User.ADMIN.getUserName(), User.ADMIN.getPassword(), User.ADMIN.getRoles())
                .withUser(User.USER.getUserName(), User.USER.getPassword(), User.USER.getRoles())
                .create();

        this.createTestQueues();
        container.stop();
    }


    private void createTestQueues() {
        JMSOperations ops = container(1).getJmsOperations();
        ops.createQueue(TEST_QUEUE_ADDRESS, TEST_QUEUE_JNDI, false);
        ops.close();
    }


    private static enum User {

        ADMIN("admin", "admin.123", "admin"),
        USER("user", "user.456", "user");

        private final String userName;

        private final String password;

        private final String[] roles;


        private User(final String userName, final String password, final String... roles) {
            this.userName = userName;
            this.password = password;
            this.roles = roles;
        }


        public String getUserName() {
            return this.userName;
        }


        public String getPassword() {
            return this.password;
        }


        public String[] getRoles() {
            return this.roles;
        }

    }

}
