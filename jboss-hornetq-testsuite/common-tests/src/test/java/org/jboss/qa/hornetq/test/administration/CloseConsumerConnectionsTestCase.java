package org.jboss.qa.hornetq.test.administration;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.clients.ReceiverAutoAck;
import org.jboss.qa.hornetq.test.prepares.PrepareBase;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.CheckFileContentUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by okalman on 11/2/15.
 */
@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class CloseConsumerConnectionsTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(CloseConsumerConnectionsTestCase.class);
    static  final String BYTEMAN_ACTION = "System.out.println(\"byteman is going to slows iterator\");Thread.sleep(1000);";


    /**
     *
     * @tpTestDetails Reproduces this issue:https://bugzilla.redhat.com/show_bug.cgi?id=1276604
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>Install BM rule which slows iteration on list of connected consumers</li>
     *     <li>start connecting new consumers</li>
     *     <li>invoke closeConsumerConnectionsForAddress via modelNode</li>
     * </ul>
     * @tpPassCrit  No exception from server is Thrown
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test  @CleanUpBeforeTest
    @RunAsClient
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Artemis Slow iteration",
                    targetClass = "org.apache.activemq.artemis.core.management.impl.ActiveMQServerControlImpl",
                    targetMethod = "closeConsumerConnectionsForAddress",
                    isAfter = true,
                    targetLocation = "INVOKE Queue.getConsumers",
                    action = BYTEMAN_ACTION),
            @BMRule(name = "Hornetq Slow iteration ",
                    targetClass = "org.hornetq.core.management.impl.HornetQServerControlImpl",
                    targetMethod = "closeConsumerConnectionsForAddress",
                    isAfter = true,
                    targetLocation = "INVOKE Queue.getConsumers",
                    action = BYTEMAN_ACTION)})

    @Prepare(value = "OneNode")
    public void closeConsumerConnectionsForAddress() throws Exception{
        container(1).start();
        ReceiverAutoAck receiver = new ReceiverAutoAck(container(1), PrepareBase.QUEUE_JNDI);
        receiver.start();
        RuleInstaller.installRule(this.getClass(), container(1));
        JMSOperations operations = container(1).getJmsOperations();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                List<Client> clientList= new ArrayList<Client>();
                for(int i =0; i <20; i++){
                    ReceiverAutoAck receiver = new ReceiverAutoAck(container(1), PrepareBase.QUEUE_JNDI);
                    receiver.start();
                    clientList.add(receiver);
                    try {
                        Thread.sleep(200);
                    }catch (Exception e){
                        log.error(e.toString());
                    }
                }
            }
        });
        t.start();

        operations.closeClientsByDestinationAddress("#");
        try {
            Thread.sleep(5000);
        }catch (Exception e){
            log.error(e.toString());
        }
        String stringToFind = "ConcurrentModificationException";
        StringBuilder pathToServerLogFile = new StringBuilder(container(1).getServerHome());
        pathToServerLogFile.append(File.separator).append("standalone").append(File.separator).append("log").append(File.separator).append("server.log");

        File serverLog = new File(pathToServerLogFile.toString());

        Assert.assertFalse("Server log cannot contain string: " + stringToFind + ". This is fail - see https://bugzilla.redhat.com/show_bug.cgi?id=1276604.",
                CheckFileContentUtils.checkThatFileContainsGivenString(serverLog, stringToFind));

        container(1).stop();

    }

}
