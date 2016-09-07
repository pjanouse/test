package org.jboss.qa.hornetq.test.journalreplication;

import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.journalreplication.configuration.AddressFullPolicy;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;

import static org.jboss.qa.hornetq.constants.Constants.*;

/**
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter NETWORK FAILURE TESTING IN REPLICATED JOURNAL - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-replicated-journal-network-failures/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19048/activemq-artemis-high-availability#testcases
 * @tpTestCaseDetails Test case focuses on journal replication in dedicated HA
 * topology. Network connection between live and backup server is stopped and
 * started several times during journal synchronization, live server is killed
 * and correct clients failover to backup is tested. This test case uses NIO
 * journal and Page address policy.
 * 
 * 
 * @author <a href="dpogrebn@redhat.com">Dmytro Pogrebniuk</a>
 *
 */
@Prepare(params = {
		@Param(name = PrepareParams.JOURNAL_TYPE, value = "NIO"),
		@Param(name = PrepareParams.ADDRESS_FULL_POLICY, value = "PAGE")
})
public class JournalReplicationNioPageTestCase extends JournalReplicationAbstract {
}