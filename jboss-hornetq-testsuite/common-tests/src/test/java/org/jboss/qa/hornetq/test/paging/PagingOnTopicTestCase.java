package org.jboss.qa.hornetq.test.paging;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients.PublisherClientAck;
import org.jboss.qa.hornetq.apps.clients.SubscriberAutoAck;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.CheckFileContentUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by okalman on 11/16/15.
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class PagingOnTopicTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(PagingOnTopicTestCase.class);


    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Do NPE for hornetq",
                    targetClass = "org.hornetq.core.paging.cursor.impl.PageSubscriptionImpl$CursorIterator",
                    targetMethod = "remove",
                    isAfter = false,
                    targetLocation = "WRITE $info",
                    helper = "org.jboss.byteman.qa.hornetq.BytemanCustomHelper",
                    binding = "instance = $this",
                    action = "System.out.println(\"--------------INJECTING NULL----------\");setToNull(instance)"),
            @BMRule(name = "Do NPE for Artemis",
                    targetClass = "org.apache.activemq.artemis.core.paging.cursor.impl.PageSubscriptionImpl$CursorIterator",
                    targetMethod = "remove",
                    isAfter = false,
                    targetLocation = "WRITE $info",
                    helper = "org.jboss.byteman.qa.hornetq.BytemanCustomHelper",
                    binding = "instance = $this",
                    action = "System.out.println(\"--------------INJECTING NULL----------\");setToNull(instance)"),
    })
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.ADDRESS_FULL_POLICY, value = "PAGE"),
            @Param(name = PrepareParams.MAX_SIZE_BYTES, value = "2048"),
            @Param(name = PrepareParams.REDELIVERY_DELAY, value = "1000"),
            @Param(name = PrepareParams.REDISTRIBUTION_DELAY, value = "1000"),
            @Param(name = PrepareParams.PAGE_SIZE_BYTES, value = "1024")
    })
    public void pagingNPETestCase() throws Exception {
        container(1).start();
        RuleInstaller.installRule(this.getClass(), container(1));
        ArrayList<PublisherClientAck> publishers = new ArrayList<PublisherClientAck>();
        ArrayList<SubscriberAutoAck> subscribers = new ArrayList<SubscriberAutoAck>();

        for(int i=0; i<10; i++){
            PublisherClientAck publisher = new PublisherClientAck(container(1), PrepareConstants.TOPIC_JNDI, 50,"publisher"+i);
            publisher.start();
            publishers.add(publisher);
        }
        for(int i=0; i<3; i++){
            SubscriberAutoAck subscriber = new SubscriberAutoAck(container(1), PrepareConstants.TOPIC_JNDI,"1","subscriber"+i);
            subscriber.start();
            subscribers.add(subscriber);
        }

        for(PublisherClientAck publisher: publishers){
            publisher.join();
        }
        for(SubscriberAutoAck subscriber: subscribers){
            subscriber.join();
        }

        container(1).stop();
        StringBuilder pathToServerLogFile = new StringBuilder(container(1).getServerHome());

        pathToServerLogFile.append(File.separator).append("standalone").append(File.separator).append("log").append(File.separator).append("server.log");

        log.info("Check server.log: " + pathToServerLogFile);

        File serverLog = new File(pathToServerLogFile.toString());

        String stringToFind = "Failed to deliver: java.lang.NullPointerException";

        Assert.assertFalse("Server log cannot contain string: " + stringToFind + ". This is fail on EAP 6.4.5 and lower - see https://bugzilla.redhat.com/show_bug.cgi?id=1276206.",
                CheckFileContentUtils.checkThatFileContainsGivenString(serverLog, stringToFind));

    }
}
