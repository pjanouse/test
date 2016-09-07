package org.jboss.qa.hornetq.test.failover;

import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.JMSOperations;

/**
 *
 * Tests failover of remote JCA and replicated journal with BLOCK policy.
 /**
 *
 * Tests failover of remote JCA and replicated journal.
 *
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter FAILOVER OF STANDALONE JMS CLIENT WITH REPLICATED JOURNAL IN DEDICATED/COLLOCATED TOPOLOGY - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-dedicated-replicated-journal/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-dedicated-replicated-journal-win/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19048/activemq-artemis-high-availability#testcases
 * @tpTestCaseDetails Tests failover of remote JCA and replicated journal with BLOCK policy.
 * This test case implements the same tests as ReplicatedDedicatedFailoverTestWithMdb
 */
@Prepare(params = {
        @Param(name = PrepareParams.ADDRESS_FULL_POLICY, value = "BLOCK")
})
public class ReplicatedDedicatedFailoverTestWithMdbWithBlockPolicy extends ReplicatedDedicatedFailoverTestWithMdb {

    protected void setAddressSettings(JMSOperations jmsAdminOperations) {
        setAddressSettings("default", jmsAdminOperations);
    }

    protected void setAddressSettings(String serverName, JMSOperations jmsAdminOperations) {
        jmsAdminOperations.addAddressSettings(serverName, "#", "BLOCK", 100 * 1024 * 1024, 0, 0, 512 * 1024);
    }

}
