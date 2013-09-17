package org.jboss.qa.hornetq.test.cli.attributes;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.management.cli.CliClient;
import org.jboss.qa.management.cli.CliConfiguration;
import org.jboss.qa.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
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
public class RemoteConnectionFactoryTestCase extends HornetQTestCase {

    private final String address = "/subsystem=messaging/hornetq-server=default/connection-factory=RemoteConnectionFactory";

    private Properties attributes;

    @Before
    public void startServer() throws InterruptedException {
        controller.start(CONTAINER1);
    }

    @After
    public void stopServer() {

        stopServer(CONTAINER1);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void connectorWriteReadAttributeTest() throws Exception {
        CliConfiguration cliConf = new CliConfiguration(CONTAINER1_IP, MANAGEMENT_PORT_EAP6);
        CliClient cliClient = new CliClient(cliConf);

        writeReadAttributeTest(cliClient, address, "connector", "{\"netty\" => undefined}");
    }
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void discoveryGroupWriteReadAttributeTest() throws Exception {
        CliConfiguration cliConf = new CliConfiguration(CONTAINER1_IP, MANAGEMENT_PORT_EAP6);
        CliClient cliClient = new CliClient(cliConf);

        writeReadAttributeTest(cliClient, address, "discovery-group-name", "dg-group1");
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void writeReadAttributeTest() throws Exception {
        writeReadAttributeTest("connectionFactoryAttributes.txt");
    }

    public void writeReadAttributeTest(String attributeFileName) throws Exception {

        attributes = new Properties();
        attributes.load(this.getClass().getResourceAsStream(attributeFileName));

        CliConfiguration cliConf = new CliConfiguration(CONTAINER1_IP, MANAGEMENT_PORT_EAP6);
        CliClient cliClient = new CliClient(cliConf);

        String value;
        for (String attributeName : attributes.stringPropertyNames()) {

            value = attributes.getProperty(attributeName);

            writeReadAttributeTest(cliClient, address, attributeName, value);

        }
    }

}
