// TODO write tests which verifies functionality of attributes
// TODO write test for interceptors attributes
package org.jboss.qa.hornetq.test.cli.attributes;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.management.cli.CliClient;
import org.jboss.qa.management.cli.CliConfiguration;
import org.jboss.qa.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.*;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.util.Properties;

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
 */

@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class CoreAttributeTestCase extends HornetQTestCase {

    @Rule
    public Timeout timeout = new Timeout(DEFAULT_TEST_TIMEOUT);

    private static final Logger log = Logger.getLogger(CoreAttributeTestCase.class);

    private final String address = "/subsystem=messaging/hornetq-server=default";

    private Properties attributes;

    CliConfiguration cliConf = new CliConfiguration(CONTAINER1_IP, MANAGEMENT_PORT_EAP6, getUsername(CONTAINER1), getPassword(CONTAINER1));

    @Before
    public void startServer() {
        controller.start(CONTAINER1);
    }

    @After
    public void stopServer() {
        controller.stop(CONTAINER1);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void stringWriteReadAttributeTest() throws Exception {
        writeReadAttributeTest("stringCliAttributes.txt");
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void longWriteReadAttributeTest() throws Exception {
        writeReadAttributeTest("longCliAttributes.txt");
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void intWriteReadAttributeTest() throws Exception {
        writeReadAttributeTest("intCliAttributes.txt");
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void booleanWriteReadAttributeTest() throws Exception {
        writeReadAttributeTest("booleanCliAttributes.txt");
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
