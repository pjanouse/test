package org.jboss.qa.artemis.test.security;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import category.Functional;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

/**
 * Test security permissions to queues and topic
 * <p/>
 * Uses its own application-roles.properties, application-roles.properties
 * <p/>
 * It creates its own address-settings in standalone-full-ha.xml, enables
 * security.
 * <p/>
 * There are 3 users and 3 roles: admin -> role (username/password) admin -
 * admin (admin/adminadmin) admin - admin (admin/useruser) user - user
 * (unauthenticated)
 * <p/>
 * There is 1 queue/topic name of queue/topic -> roles -> permission for the
 * role testQueue0 -> user -> send,consume -> admin -> all permissions -> admin
 * -> send, consume, create/delete durable queue
 *
 * @author mnovak@rehat.com
 * @tpChapter Security testing
 * @tpSubChapter HORNETQ ADDRESS SETTINGS AUTHENTICATION
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19042/activemq-artemis-integration#testcases
 * @tpTestCaseDetails Test security permissions to queues and topic. Create 3
 * users on the server - admin, user and guest. Create address settings for all
 * destinations (mask #) as follows: guest can send and receive, user can send,
 * receive and create/destroy non-durable destinations, admin can do anything
 * (eg. create/destroy durable destinations). Log in with one of the users and
 * try to execute operations.
 */
@RunWith(Arquillian.class)
@Category(Functional.class)
public class PermissionSecurityTestCase extends PermissionSecurityTestBase {

    private static final Logger logger = Logger.getLogger(PermissionSecurityTestCase.class);

    /**
     * This test will start one server. And try to send/receive messages or
     * create/delete queue from it.
     *
     * @tpTestDetails There is only one server with 3 users - admin, user and
     * guest. Create address settings for all destinations - guest can only send
     * and receive messages. Log in as guest and try to send/receive messages or
     * create/delete queue from it.
     * @tpProcedure <ul>
     * <li>Start server with configured users and address settings</li>
     * <li>Log as guest and try to:
     * <ul>
     * <li>Send and receive 10 messages</li>
     * <li>Create durable queue</li>
     * <li>Delete said durable queue</li>
     * <li>Create non durable queue</li>
     * <li>Delete said non durable queue</li>
     * </ul>
     * </li>
     * </ul>
     * @tpPassCrit Only sending and receiving message work, anything else should fail
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.ENABLE_SECURITY, value = "true"),
            @Param(name = PrepareParams.SECURITY_GUEST_CONSUME, value = "true"),
            @Param(name = PrepareParams.SECURITY_GUEST_CREATE_DURABLE_QUEUE, value = "false"),
            @Param(name = PrepareParams.SECURITY_GUEST_CREATE_NON_DURABLE_QUEUE, value = "false"),
            @Param(name = PrepareParams.SECURITY_GUEST_DELETE_DURABLE_QUEUE, value = "false"),
            @Param(name = PrepareParams.SECURITY_GUEST_DELETE_NON_DURABLE_QUEUE, value = "false"),
            @Param(name = PrepareParams.SECURITY_GUEST_MANAGE, value = "false"),
            @Param(name = PrepareParams.SECURITY_GUEST_SEND, value = "true")
    })
    public void testSecurityWithGuest() throws Exception {
        securityWithGuest();
    }

    /**
     * This test will start one server. And try to send/receive messages or
     * create/delete queue from it.
     *
     * @tpTestDetails There is only one server with 3 users - admin, user and
     * guest. Create address settings for all destinations - user can send,
     * receive and create/destroy non-durable destinations. Log in as user and
     * try to to send/receive messages or create/delete queue from it.
     * @tpProcedure <ul>
     * <li>Start server with configured users and address settings</li>
     * <li>Log as user and try to:
     * <ul>
     * <li>Send and receive 10 messages</li>
     * <li>Create durable queue</li>
     * <li>Delete said durable queue</li>
     * <li>Create non durable queue</li>
     * <li>Delete said non durable queue</li>
     * </ul>
     * </li>
     * </ul>
     * @tpPassCrit Only sending and receiving messages and creating and deleting
     * non-durable queue should work, trying to create or delete durable queue
     * should fail
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.ENABLE_SECURITY, value = "true"),
            @Param(name = PrepareParams.SECURITY_USERS_CONSUME, value = "true"),
            @Param(name = PrepareParams.SECURITY_USERS_CREATE_DURABLE_QUEUE, value = "false"),
            @Param(name = PrepareParams.SECURITY_USERS_CREATE_NON_DURABLE_QUEUE, value = "true"),
            @Param(name = PrepareParams.SECURITY_USERS_DELETE_DURABLE_QUEUE, value = "false"),
            @Param(name = PrepareParams.SECURITY_USERS_DELETE_NON_DURABLE_QUEUE, value = "true"),
            @Param(name = PrepareParams.SECURITY_USERS_MANAGE, value = "true"),
            @Param(name = PrepareParams.SECURITY_USERS_SEND, value = "true")
    })
    public void testSecurityWithUser() throws Exception {
        securityWithUser();
    }

    /**
     * This test will start one server. And try to send/receive messages or
     * create/delete queue from it.
     *
     * @tpTestDetails There is only one server with 3 users - admin, user and
     * guest. Create address settings for all destinations - admin can do
     * everything. Log in as admin and try to to send/receive messages or
     * create/delete queue from it.
     * @tpProcedure <ul>
     * <li>Start server with configured users and address settings</li>
     * <li>Log as admin and try to:
     * <ul>
     * <li>Send and receive 10 messages</li>
     * <li>Create durable queue</li>
     * <li>Delete said durable queue</li>
     * <li>Create non durable queue</li>
     * <li>Delete said non durable queue</li>
     * </ul>
     * </li>
     * </ul>
     * @tpPassCrit All operations have to succeed
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.ENABLE_SECURITY, value = "true"),
            @Param(name = PrepareParams.SECURITY_ADMIN_CONSUME, value = "true"),
            @Param(name = PrepareParams.SECURITY_ADMIN_CREATE_DURABLE_QUEUE, value = "true"),
            @Param(name = PrepareParams.SECURITY_ADMIN_CREATE_NON_DURABLE_QUEUE, value = "true"),
            @Param(name = PrepareParams.SECURITY_ADMIN_DELETE_DURABLE_QUEUE, value = "true"),
            @Param(name = PrepareParams.SECURITY_ADMIN_DELETE_NON_DURABLE_QUEUE, value = "true"),
            @Param(name = PrepareParams.SECURITY_ADMIN_MANAGE, value = "true"),
            @Param(name = PrepareParams.SECURITY_ADMIN_SEND, value = "true")
    })
    public void testSecurityWithAdmin() throws Exception {
        securityWithAdmin();
    }

    /**
     * @tpTestDetails There is only one server with 3 users - admin, user and
     * guest. Create address settings for all destinations. Deploy MDB which
     * sends messages from InQueue to OutQueue. Log in as user and send messages
     * to InQueue. Check number of messages in OutQueue.
     * @tpProcedure <ul>
     * <li>Start server with configured users and address settings</li>
     * <li>Deploy MDB</li>
     * <li>Log in as user and send messages to InQueue</li>
     * <li>Check number of messages in OutQueue</li>
     * </ul>
     * @tpPassCrit OutQueue is empty. Mdb shouldn't be able to send any message to OutQueue.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    //TODO This test will fail on EAP 6.4.0.DR13 and older
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.ENABLE_SECURITY, value = "true"),
            @Param(name = PrepareParams.SECURITY_USERS_CONSUME, value = "true"),
            @Param(name = PrepareParams.SECURITY_USERS_CREATE_DURABLE_QUEUE, value = "false"),
            @Param(name = PrepareParams.SECURITY_USERS_CREATE_NON_DURABLE_QUEUE, value = "true"),
            @Param(name = PrepareParams.SECURITY_USERS_DELETE_DURABLE_QUEUE, value = "false"),
            @Param(name = PrepareParams.SECURITY_USERS_DELETE_NON_DURABLE_QUEUE, value = "true"),
            @Param(name = PrepareParams.SECURITY_USERS_MANAGE, value = "true"),
            @Param(name = PrepareParams.SECURITY_USERS_SEND, value = "true")
    })
    public void testInVmSecurityTestCase() throws Exception {
        inVmSecurityTestCase();
    }
}