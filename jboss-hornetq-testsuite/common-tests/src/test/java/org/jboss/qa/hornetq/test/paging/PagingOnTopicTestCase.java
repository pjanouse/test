package org.jboss.qa.hornetq.test.paging;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.byteman.qa.hornetq.BytemanCustomHelper;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients.PublisherClientAck;
import org.jboss.qa.hornetq.apps.clients.SubscriberAutoAck;
import org.jboss.qa.hornetq.tools.CheckFileContentUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Before;
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
    final String TOPIC = "pageTopic";
    final String TOPIC_JNDI = "/topic/pageTopic";
    final String ADDRESS = "jms.topic." + TOPIC;

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
    public void pagingNPETestCase() throws Exception {
        container(1).start();
        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        jmsAdminOperations.cleanupTopic(TOPIC);
        jmsAdminOperations.createTopic(TOPIC, TOPIC_JNDI);
        jmsAdminOperations.removeAddressSettings(ADDRESS);
        jmsAdminOperations.addAddressSettings(ADDRESS, "PAGE", 2048, 1000, 1000, 1024);
        container(1).stop();
        container(1).start();
        RuleInstaller.installRule(this.getClass(), container(1));
        ArrayList<PublisherClientAck> publishers = new ArrayList<PublisherClientAck>();
        ArrayList<SubscriberAutoAck> subscribers = new ArrayList<SubscriberAutoAck>();

        for(int i=0; i<10; i++){
            PublisherClientAck publisher = new PublisherClientAck(container(1),TOPIC_JNDI,50,"publisher"+i);
            publisher.start();
            publishers.add(publisher);
        }
        for(int i=0; i<3; i++){
            SubscriberAutoAck subscriber = new SubscriberAutoAck(container(1),TOPIC_JNDI,"1","subscriber"+i);
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


    @RunAsClient
    @Before
    public void prepare(){
        createExternalJarForByteman();
    }

    public static JavaArchive createExternalJarForByteman() {
        JavaArchive lib = ShrinkWrap.create(JavaArchive.class, "external.jar");
        lib.addClass(BytemanCustomHelper.class);
        File target = new File("/tmp/" + "external" + ".jar");
        if (target.exists()) {
            target.delete();
        }
        lib.as(ZipExporter.class).exportTo(target, true);
        return lib;


    }



}
