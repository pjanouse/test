package org.jboss.qa.hornetq.test.failover;

import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;

/**
 *
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter FAILOVER OF  STANDALONE JMS CLIENT WITH REPLICATED JOURNAL IN DEDICATED/COLLOCATED TOPOLOGY - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-colocated-cluster-replicated-journal/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-colocated-cluster-replicated-journal-win/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5535/hornetq-high-availability#testcases
 * @tpTestCaseDetails This test case implements  all tests from ReplicatedColocatedClusterFailoverTestCase, only BLOCK
 * policy instead of PAGE is set in address settings.
 */
@Prepare(params = {
        @Param(name = PrepareParams.ADDRESS_FULL_POLICY, value = "BLOCK")
})
public class ReplicatedColocatedClusterFailoverTestCaseWithBlockPolicy extends ReplicatedColocatedClusterFailoverTestCase {

}
