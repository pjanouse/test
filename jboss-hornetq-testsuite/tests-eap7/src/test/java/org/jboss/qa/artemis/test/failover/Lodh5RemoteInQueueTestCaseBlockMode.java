package org.jboss.qa.artemis.test.failover;

import org.jboss.qa.hornetq.tools.JMSOperations;

/**
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter XA TRANSACTION RECOVERY TESTING WITH HORNETQ RESOURCE ADAPTER - TEST SCENARIOS (LODH SCENARIOS)
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-lodh5-remote-inque/           /
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19047/activemq-artemis-functional#testcases
 */
public class Lodh5RemoteInQueueTestCaseBlockMode extends Lodh5RemoteInQueueTestCase{

    @Override
    protected  void setAddressSettings(JMSOperations jmsAdminOperations) {
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "BLOCK", MAX_SIZE_BYTES_DEFAULT, 0, 0, PAGE_SIZE_BYTES_DEFAULT);
    }
}
