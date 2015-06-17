package org.jboss.qa.hornetq.test.failover;

import org.jboss.qa.hornetq.tools.JMSOperations;

/**
 *
 * Tests failover of remote JCA and replicated journal with BLOCK policy.
 /**
 *
 * Tests failover of remote JCA and replicated journal.
 *
 * @tpChapter Recovery/Failover testing
 * @tpSubChapter FAILOVER OF STANDALONE JMS CLIENT WITH REPLICATED JOURNAL IN DEDICATED/COLLOCATED TOPOLOGY - TEST SCENARIOS
 * @tpJobLink tbd
 * @tpTcmsLink tbd
 * @tpTestCaseDetails Tests failover of remote JCA and replicated journal with BLOCK policy.
 * This test case implements the same tests as ReplicatedDedicatedFailoverTestWithMdb
 */
public class ReplicatedDedicatedFailoverTestWithMdbWithBlockPolicy extends ReplicatedDedicatedFailoverTestWithMdb {

    protected void setAddressSettings(JMSOperations jmsAdminOperations) {
        setAddressSettings("default", jmsAdminOperations);
    }

    protected void setAddressSettings(String serverName, JMSOperations jmsAdminOperations) {
        jmsAdminOperations.addAddressSettings(serverName, "#", "BLOCK", 100 * 1024 * 1024, 0, 0, 512 * 1024);
    }

}
