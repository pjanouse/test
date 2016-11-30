package org.jboss.qa.hornetq.test.cluster;

import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.jms.*;
import javax.naming.Context;


/**
 * This test case can be run with IPv6 - just replace those environment variables for ipv6 ones:
 * export MYTESTIP_1=$MYTESTIPV6_1
 * export MYTESTIP_2=$MYTESTIPV6_2
 * export MCAST_ADDR=$MCAST_ADDRIPV6
 * <p/>
 * This test also serves
 *
 * @author nziakova@redhat.com
 * @tpChapter  Integration testing
 * @tpSubChapter JGROUPS CLUSTER - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-cluster-ipv6-tests/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-cluster-tests/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19042/activemq-artemis-integration#testcases
 * @tpTestCaseDetails This is the same as ClusterTestCase, JGroups is used for cluster nodes discovery.
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Prepare(value = "FourNodes", params = {
        @Param(name = PrepareParams.CLUSTER_TYPE, value = "JGROUPS_DISCOVERY")
})
public class JGroupsClusterTestCase extends ClusterTestCase {

    // TODO un-ignore when bz https://bugzilla.redhat.com/show_bug.cgi?id=1132190 is fixed
    @Ignore
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Category(FunctionalTests.class)
    public void testLookupOfConnectionFactoryWithJGroupsDiscoveryGroup() throws Exception {

        container(1).start();

        Context context = null;
        Connection connection = null;

        try {
            context = container(1).getContext();

            Queue queue = (Queue) context.lookup(PrepareConstants.IN_QUEUE_JNDI);

            ConnectionFactory connectionFactory = (ConnectionFactory) context.lookup("jms/" + PrepareConstants.REMOTE_CONNECTION_FACTORY_NAME);

            connection = connectionFactory.createConnection();

            connection.start();

            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

            MessageProducer producer = session.createProducer(queue);

            producer.send(session.createTextMessage());

            session.commit();

            MessageConsumer consumer = session.createConsumer(queue);

            Message message = consumer.receive(3000);

            session.commit();

            Assert.assertNotNull("Message cannot be null", message);

        } finally {
            if (context != null)    {
                context.close();
            }
            if (connection != null) {
                connection.close();
            }
            container(1).stop();
        }
    }
}