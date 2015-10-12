// TODO write tests which verifies functionality of attributes
// TODO write test for interceptors attributes
package org.jboss.qa.artemis.test.cli.attributes;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.test.cli.CliTestBase;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.management.cli.CliClient;
import org.jboss.qa.management.cli.CliConfiguration;
import org.jboss.qa.management.cli.CliUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.lang.String;
import java.util.Properties;

import static org.junit.Assert.*;

/**
 * Test write, read operation for core messaging attributes.
 * <p/>
 * active                                 true                                            BOOLEAN
 * allow-failback                         true                                            BOOLEAN
 * async-connection-execution-enabled     true                                            BOOLEAN
 * backup                                 false                                           BOOLEAN
 * check-for-live-server                  false                                           BOOLEAN
 * clustered                              true                                            BOOLEAN
 * create-bindings-dir                    true                                            BOOLEAN
 * create-journal-dir                     true                                            BOOLEAN
 * failover-on-shutdown                   false                                           BOOLEAN
 * jmx-management-enabled                 false                                           BOOLEAN
 * journal-sync-non-transactional         true                                            BOOLEAN
 * journal-sync-transactional             true                                            BOOLEAN
 * log-journal-write-rate                 false                                           BOOLEAN
 * message-counter-enabled                false                                           BOOLEAN
 * persist-delivery-count-before-delivery false                                           BOOLEAN
 * persist-id-cache                       true                                            BOOLEAN
 * persistence-enabled                    true                                            BOOLEAN
 * run-sync-speed-test                    false                                           BOOLEAN
 * security-enabled                       false                                           BOOLEAN
 * shared-store                           true                                            BOOLEAN
 * started                                true                                            BOOLEAN
 * wild-card-routing-enabled              true                                            BOOLEAN
 * <p/>
 * backup-group-name                      undefined                                       STRING
 * cluster-password                       ${jboss.messaging.cluster.password:CHANGE ME!!} STRING
 * cluster-user                           HORNETQ.CLUSTER.ADMIN.USER                      STRING
 * jmx-domain                             org.hornetq                                     STRING
 * journal-type                           ASYNCIO                                         STRING
 * management-address                     jms.queue.hornetq.management                    STRING
 * management-notification-address        hornetq.notifications                           STRING
 * replication-clustername                undefined                                       STRING
 * version                                2.3.5.Final-redhat-2 (Monster Bee, 123)         STRING
 * security-domain                        other                                           STRING
 * <p/>
 * connection-ttl-override                -1                                              LONG
 * failback-delay                         5000                                            LONG
 * journal-buffer-size                    undefined                                       LONG
 * journal-buffer-timeout                 undefined                                       LONG
 * journal-file-size                      10240000                                        LONG
 * memory-measure-interval                -1                                              LONG
 * message-counter-sample-period          10000                                           LONG
 * message-expiry-scan-period             30000                                           LONG
 * security-invalidation-interval         10000                                           LONG
 * server-dump-interval                   -1                                              LONG
 * transaction-timeout                    300000                                          LONG
 * transaction-timeout-scan-period        1000                                            LONG
 * <p/>
 * id-cache-size                          20000                                           INT
 * journal-compact-min-files              10                                              INT
 * journal-compact-percentage             30                                              INT
 * journal-max-io                         undefined                                       INT
 * journal-min-files                      2                                               INT
 * memory-warning-threshold               25                                              INT
 * message-counter-max-day-history        10                                              INT
 * message-expiry-thread-priority         3                                               INT
 * page-max-concurrent-io                 5                                               INT
 * perf-blast-pages                       -1                                              INT
 * scheduled-thread-pool-max-size         5                                               INT
 * thread-pool-max-size                   30                                              INT
 * <p/>
 * remoting-incoming-interceptors         undefined                                       LIST
 * remoting-interceptors                  undefined                                       LIST
 * remoting-outgoing-interceptors         undefined                                       LIST
 * 
 * 
 * @tpChapter Integration testing
 * @tpSubChapter Administration of HornetQ component
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-ipv6-tests/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19042/activemq-artemis-integration#testcases
 * @tpTestCaseDetails Test write, read operation for core messaging attributes: Tested attributes :
 * active, allow-failback, async-connection-execution-enabled, backup,
 * check-for-live-server, clustered, create-bindings-dir, create-journal-dir,
 * failover-on-shutdown, jmx-management-enabled, journal-sync-non-transactional,
 * journal-sync-transactional, log-journal-write-rate, message-counter-enabled,
 * persist-delivery-count-before-delivery, persist-id-cache,
 * persistence-enabled, run-sync-speed-test, security-enabled, shared-store,
 * started, wild-card-routing-enabled, backup-group-name, cluster-password,
 * cluster-user, jmx-domain, journal-type, management-address,
 * management-notification-address, replication-clustername, version,
 * security-domain, connection-ttl-override, failback-delay,
 * journal-buffer-size, journal-buffer-timeout, journal-file-size,
 * memory-measure-interval, message-counter-sample-period,
 * message-expiry-scan-period, security-invalidation-interval,
 * server-dump-interval, transaction-timeout, transaction-timeout-scan-period,
 * id-cache-size, journal-compact-min-files, journal-compact-percentage,
 * journal-max-io, journal-min-files, memory-warning-threshold,
 * message-counter-max-day-history, message-expiry-thread-priority,
 * ,page-max-concurrent-io, perf-blast-pages, scheduled-thread-pool-max-size,
 * remoting-incoming-interceptors, remoting-interceptors,
 * remoting-outgoing-interceptors
 *
 *
 */

@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Category(FunctionalTests.class)
public class CoreAttributeTestCase extends CliTestBase {

    @Rule
    public Timeout timeout = new Timeout(DEFAULT_TEST_TIMEOUT);

    private static final Logger log = Logger.getLogger(CoreAttributeTestCase.class);

    private final String address = "/subsystem=messaging-activemq/server=default";

    private Properties attributes;

    CliConfiguration cliConf = new CliConfiguration(container(1).getHostname(), container(1).getPort(), container(1).getUsername(), container(1).getPassword());

    @Before
    public void startServer() {
        container(1).stop();
        container(1).start();
    }

    @After
    public void stopServer() {
        container(1).stop();
    }
    /** 
     * @tpTestDetails Test write, read operation for core messaging attributes. Type of attribute : String
     * @tpProcedure <ul>
     * <li>start one server</li>
     * <li>connect to CLI</li>
     * <li>Try to read/write attributes</li>
     * <li>stop server<li/>
     * </ul>
     * @tpPassCrit reading and writing attributes is successful
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void stringWriteReadAttributeTest() throws Exception {
        writeReadAttributeTest("/stringCliAttributes.txt");
    }
   /** 
     * @tpTestDetails Test write, read operation for core messaging attributes. Type of attribute : Long
     * @tpProcedure <ul>
     * <li>start one server</li>
     * <li>connect to CLI</li>
     * <li>Try to read/write attributes</li>
     * <li>stop server<li/>
     * </ul>
     * @tpPassCrit reading and writing attributes is successful
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void longWriteReadAttributeTest() throws Exception {
        writeReadAttributeTest("/longCliAttributes.txt");
    }
    /** 
     * @tpTestDetails Test write, read operation for core messaging attributes. Type of attribute : int
     * @tpProcedure <ul>
     * <li>start one server</li>
     * <li>connect to CLI</li>
     * <li>Try to read/write attributes</li>
     * <li>stop server<li/>
     * </ul>
     * @tpPassCrit reading and writing attributes is successful
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void intWriteReadAttributeTest() throws Exception {
        writeReadAttributeTest("/intCliAttributes.txt");
    }
    /** 
     * @tpTestDetails Test write, read operation for core messaging attributes. Type of attribute : boolean
     * @tpProcedure <ul>
     * <li>start one server</li>
     * <li>connect to CLI</li>
     * <li>Try to read/write attributes</li>
     * <li>stop server<li/>
     * </ul>
     * @tpPassCrit reading and writing attributes is successful
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void booleanWriteReadAttributeTest() throws Exception {
        writeReadAttributeTest("/booleanCliAttributes.txt");
    }

    /**
     * @tpTestDetails Test whether CLI doesn't allow illegal values for attributes.
     * @tpProcedure <ul>
     * <li>start one server</li>
     * <li>connect to CLI</li>
     * <li>Try to write illegal values for attributes</li>
     * <li>stop server</li>
     * </ul>
     * @tpPassCrit all writeAttribute operations must fail
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void illegalValueWriteAttributeTest() throws Exception {
        Properties attributes = new Properties();
        attributes.load(this.getClass().getResourceAsStream("/illegalValueCliAttributes.txt"));

        CliClient cliClient = new CliClient(cliConf);

        for (String attributeName : attributes.stringPropertyNames()) {
            String[] values = attributes.getProperty(attributeName).split(",");

            for (String value : values) {
                if (cliClient.writeAttribute(address, attributeName, value)) {
                    String command = CliUtils.buildCommand(address, ":write-attribute",
                            new String[]{"name=" + attributeName, "value=" + value});
                    fail("Operation \"" + command + "\" should not be permitted.");
                }
            }
        }
    }

    public void writeReadAttributeTest(String attributeFileName) throws Exception {

        attributes = new Properties();
        attributes.load(this.getClass().getResourceAsStream(attributeFileName));

        CliClient cliClient = new CliClient(cliConf);

        String value;
        for (String attributeName : attributes.stringPropertyNames()) {

            value = attributes.getProperty(attributeName);

            writeReadAttributeTest(cliClient, address, attributeName, value);

        }
    }

}
