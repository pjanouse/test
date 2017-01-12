package org.jboss.qa.artemis.test.failover;

import category.Lodh5DoubleSendToDb;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.junit.experimental.categories.Category;

/**
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter XA TRANSACTION RECOVERY TESTING WITH HORNETQ RESOURCE ADAPTER - TEST SCENARIOS (LODH SCENARIOS)
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-lodh5-double-send-to-db/           /
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19047/activemq-artemis-functional#testcases
 */
@Category(Lodh5DoubleSendToDb.class)
public class Lodh5DoubleSendToDbTestCaseBlockMode extends Lodh5DoubleSendToDbTestCase{

    @Override
    protected  void setAddressSettings(JMSOperations jmsAdminOperations) {
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("default", "#", "BLOCK", MAX_SIZE_BYTES_DEFAULT, 0, 0, PAGE_SIZE_BYTES_DEFAULT,  "jms.queue.ExpiryQueue", "jms.queue.DLQ", -1);
    }
}
