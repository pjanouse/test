package org.jboss.qa.hornetq.test.journalreplication;

import org.jboss.qa.hornetq.test.journalreplication.configuration.AddressFullPolicy;
import org.jboss.qa.hornetq.test.journalreplication.configuration.JournalType;

/**
 * @tpChapter Recovery/Failover testing
 * @tpSubChapter NETWORK FAILURE TESTING IN REPLICATED JOURNAL - TEST SCENARIOS
 * @tpJobLink tbd
 * @tpTcmsLink tbd
 * @tpTestCaseDetails Test case focuses on journal replication in dedicated HA
 * topology. Network connection between live and backup server is stopped and
 * started several times during journal synchronization, live server is killed
 * and correct clients failover to backup is tested. This test case uses ASYNCNIO
 * journal and Block address policy.
 * 
 * 
 * @author <a href="dpogrebn@redhat.com">Dmytro Pogrebniuk</a>
 *
 */
public class JournalReplicationAsyncioBlockTestCase extends JournalReplicationAbstract
{
	@Override
	public JournalType getJournalType()
	{
		return JournalType.ASYNCIO;
	}

	@Override
	public AddressFullPolicy getAddressFullPolicy()
	{
		return AddressFullPolicy.BLOCK;
	}
	
}
