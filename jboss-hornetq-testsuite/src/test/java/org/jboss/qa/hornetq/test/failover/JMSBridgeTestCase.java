package org.jboss.qa.hornetq.test.failover;

import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.tools.JMSOperations;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests JMS bridge -
 * failover
 * deploy/un-deploy
 *
 */
@RunWith(Arquillian.class)
public class JMSBridgeTestCase extends DedicatedFailoverCoreBridges {

    @Override
    protected void deployBridge(String containerName, boolean useDiscovery) {

        String bridgeName = "myBridge";
        String sourceConnectionFactory = "java:/ConnectionFactory";
        String sourceDestination = inQueueJndiName;
//        Map<String,String> sourceContext = new HashMap<String, String>();
//        sourceContext.put("java.naming.factory.initial", "org.jboss.naming.remote.client.InitialContextFactory");
//        sourceContext.put("java.naming.provider.url", "remote://" + getHostname(containerName) + ":4447");

        String targetConnectionFactory = "jms/RemoteConnectionFactory";
        String targetDestination = outQueueJndiName;
        Map<String,String> targetContext = new HashMap<String, String>();
        targetContext.put("java.naming.factory.initial", "org.jboss.naming.remote.client.InitialContextFactory");
        if (CONTAINER1.equalsIgnoreCase(containerName)) { // if deployed to container 1 then target is container 3
            targetContext.put("java.naming.provider.url", "remote://" + CONTAINER3_IP + ":4447");
        } else if (CONTAINER2.equalsIgnoreCase(containerName)) { // if deployed to container 2 then target is container 3
            targetContext.put("java.naming.provider.url", "remote://" + CONTAINER3_IP + ":4447");
        } else if (CONTAINER3.equalsIgnoreCase(containerName)) { // if deployed to container 3 then target is container 1
            targetContext.put("java.naming.provider.url", "remote://" + CONTAINER1_IP + ":4447");
        }

        String qualityOfService = "ONCE_AND_ONLY_ONCE";
        long failureRetryInterval = 1000;
        int maxRetries = 10;
        long maxBatchSize = 10;
        long maxBatchTime = 100;
        boolean addMessageIDInHeader = true;

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.createJMSBridge(bridgeName, sourceConnectionFactory, sourceDestination, null,
                targetConnectionFactory, targetDestination, targetContext, qualityOfService, failureRetryInterval, maxRetries,
                maxBatchSize, maxBatchTime, addMessageIDInHeader);

        jmsAdminOperations.close();

        stopServer(containerName);
    }

}
