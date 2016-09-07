package org.jboss.qa.hornetq.test.failover;

import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.junit.runner.RunWith;

/**
* Failover tests just with replicated journal with BLOCK policy.
*/
/**
 *
 * @tpChapter   RECOVERY/FAILOVER TESTING
 * @tpSubChapter FAILOVER OF  STANDALONE JMS CLIENT WITH REPLICATED JOURNAL IN DEDICATED/COLLOCATED TOPOLOGY - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-dedicated-replicated-journal/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-dedicated-replicated-journal-win/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5535/hornetq-high-availability#testcases
 * @tpTestCaseDetails This TestCase implements the same set of tests as ReplicatedDedicatedFailoverTestCase. BLOCK policy
 * is set to addresses
 */
@RunWith(Arquillian.class)
@Prepare(params = {
        @Param(name = PrepareParams.ADDRESS_FULL_POLICY, value = "BLOCK")
})
public class ReplicatedDedicatedFailoverTestCaseWithBlockPolicy extends ReplicatedDedicatedFailoverTestCase {


    protected void setAddressSettings(JMSOperations jmsAdminOperations) {
        setAddressSettings("default", jmsAdminOperations);
    }

    protected void setAddressSettings(String serverName, JMSOperations jmsAdminOperations) {
        jmsAdminOperations.addAddressSettings(serverName, "#", "BLOCK", 100 * 1024 * 1024, 0, 0, 512 * 1024);
    }
}
