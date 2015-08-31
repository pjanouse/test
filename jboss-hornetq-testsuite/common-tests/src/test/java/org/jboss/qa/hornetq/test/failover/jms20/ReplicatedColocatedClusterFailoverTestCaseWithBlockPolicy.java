package org.jboss.qa.hornetq.test.failover.jms20;

import org.jboss.qa.hornetq.tools.JMSOperations;

/**
 *
 * @tpChapter   RECOVERY/FAILOVER TESTING
 * @tpSubChapter FAILOVER OF  STANDALONE JMS CLIENT WITH REPLICATED JOURNAL IN DEDICATED/COLLOCATED TOPOLOGY - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP6/view/EAP6-HornetQ/job/eap-60-hornetq-ha-failover-colocated-cluster-replicated-journal/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5535/hornetq-high-availability#testcases
 * @tpTestCaseDetails This test case implements  all tests from ReplicatedColocatedClusterFailoverTestCase, only BLOCK
 * policy instead of PAGE is set in address settings.
 */
public class ReplicatedColocatedClusterFailoverTestCaseWithBlockPolicy extends ReplicatedColocatedClusterFailoverTestCase {

    protected void setAddressSettings(JMSOperations jmsAdminOperations) {
        setAddressSettings("default", jmsAdminOperations);
    }

    protected void setAddressSettings(String serverName, JMSOperations jmsAdminOperations) {
        jmsAdminOperations.addAddressSettings(serverName, "#", "BLOCK", 100 * 1024 * 1024, 0, 0, 512 * 1024);
    }

}
