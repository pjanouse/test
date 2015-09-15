package org.jboss.qa.hornetq.test.journalreplication;

import org.jboss.qa.hornetq.test.journalreplication.configuration.AddressFullPolicy;
import org.jboss.qa.hornetq.test.journalreplication.configuration.JournalType;

/**
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter NETWORK FAILURE TESTING IN REPLICATED JOURNAL - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-replicated-journal-network-failures/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19048/activemq-artemis-high-availability#testcases
 * @tpTestCaseDetails Test case focuses on journal replication in dedicated HA
 * topology. Network connection between live and backup server is stopped and
 * started several times during journal synchronization, live server is killed
 * and correct clients failover to backup is tested. This test case uses NIO
 * journal and Block address policy.
 * 
 * 
 * @author <a href="dpogrebn@redhat.com">Dmytro Pogrebniuk</a>
 *
 */
public class JournalReplicationNioBlockTestCase extends JournalReplicationAbstract
{

	@Override
	public JournalType getJournalType()
	{
		return JournalType.NIO;
	}

	@Override
	public AddressFullPolicy getAddressFullPolicy()
	{
		return AddressFullPolicy.BLOCK;
	}

}
