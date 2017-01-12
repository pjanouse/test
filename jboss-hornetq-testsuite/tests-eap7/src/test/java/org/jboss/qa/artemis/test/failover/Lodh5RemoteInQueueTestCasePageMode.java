package org.jboss.qa.artemis.test.failover;

import category.Lodh5RemoteInQueue;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.junit.experimental.categories.Category;

/**
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter XA TRANSACTION RECOVERY TESTING WITH HORNETQ RESOURCE ADAPTER - TEST SCENARIOS (LODH SCENARIOS)
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-lodh5-remote-inque/           /
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19047/activemq-artemis-functional#testcases
 */
@Category(Lodh5RemoteInQueue.class)
public class Lodh5RemoteInQueueTestCasePageMode extends Lodh5RemoteInQueueTestCase{

    @Override
    protected  void setAddressSettings(JMSOperations jmsAdminOperations) {
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", MAX_SIZE_BYTES_PAGING, 0, 0, PAGE_SIZE_BYTES_PAGING);
    }
}
