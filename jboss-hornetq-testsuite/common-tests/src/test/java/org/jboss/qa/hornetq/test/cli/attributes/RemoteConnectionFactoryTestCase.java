//TODO create test for setting discovery group and connectors
package org.jboss.qa.hornetq.test.cli.attributes;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.cli.CliTestBase;
import org.jboss.qa.management.cli.CliClient;
import org.jboss.qa.management.cli.CliConfiguration;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.util.Properties;

/**
 * Test attributes on remote connectio factory:
 * ATTRIBUTE           VALUE                         TYPE
 auto-group=false
 block-on-acknowledge=true
 block-on-durable-send=true
 block-on-non-durable-send=false
 cache-large-message-client=false
 call-failover-timeout=-1
 call-timeout=30000
 client-failure-check-period=30000
 client-id=undefined
 compress-large-messages=false
 confirmation-window-size=-1
 connection-load-balancing-policy-class-name=org.hornetq.api.core.client.loadbalance.RoundRobinConnectionLoadBalancingPolicy
 connection-ttl=60000
 connector={"netty" => undefined}
 consumer-max-rate=-1
 consumer-window-size=1048576
 discovery-group-name=undefined
 discovery-initial-wait-timeout=undefined
 dups-ok-batch-size=1048576
 entries=["java:jboss/exported/jms/RemoteConnectionFactory"]
 factory-type=GENERIC
 failover-on-initial-connection=false
 failover-on-server-shutdown=undefined
 group-id=undefined
 ha=true
 initial-message-packet-size=1500
 max-retry-interval=2000
 min-large-message-size=102400
 pre-acknowledge=false
 producer-max-rate=-1
 producer-window-size=65536
 reconnect-attempts=-1
 retry-interval=1000
 retry-interval-multiplier=1.0
 scheduled-thread-pool-max-size=5
 thread-pool-max-size=30
 transaction-batch-size=1048576
 use-global-pools=true
 *
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Category(FunctionalTests.class)
public class RemoteConnectionFactoryTestCase extends CliTestBase {

    private static final Logger logger = Logger.getLogger(RemoteConnectionFactoryTestCase.class);

    @Rule
    public Timeout timeout = new Timeout(DEFAULT_TEST_TIMEOUT);

    private Properties attributes;

    CliConfiguration cliConf = new CliConfiguration(container(1).getHostname(), container(1).getPort(), container(1).getUsername(), container(1).getPassword());

    @Before
    public void startServer() throws InterruptedException {
        container(1).start();
    }

    @After
    public void stopServer() {
        container(1).stop();
    }



    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void writeReadAttributePooledConnectionFactoryTest() throws Exception {

        String address = "/subsystem=messaging/hornetq-server=default/pooled-connection-factory=hornetq-ra";

        writeReadAttributeTest(address, "/pooledConnectionFactotryAttributes.txt");
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void writeReadAttributeJmsConnectionFactoryTest() throws Exception {
        String address = "/subsystem=messaging/hornetq-server=default/connection-factory=RemoteConnectionFactory";

        writeReadAttributeTest(address,"/connectionFactoryAttributes.txt");
    }


    public void writeReadAttributeTest(String address, String attributeFileName) throws Exception {

        attributes = new Properties();
        attributes.load(this.getClass().getResourceAsStream(attributeFileName));

        CliClient cliClient = new CliClient(cliConf);

        String value;
        for (String attributeName : attributes.stringPropertyNames()) {

            value = attributes.getProperty(attributeName);

            logger.info("Test attribute: " + attributeName + " with value: " + value);

            writeReadAttributeTest(cliClient, address, attributeName, value);

        }
    }

}
