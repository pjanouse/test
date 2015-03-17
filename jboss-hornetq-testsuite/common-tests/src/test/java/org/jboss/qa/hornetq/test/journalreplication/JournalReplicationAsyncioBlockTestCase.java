package org.jboss.qa.hornetq.test.journalreplication;

import org.jboss.qa.hornetq.test.journalreplication.configuration.AddressFullPolicy;
import org.jboss.qa.hornetq.test.journalreplication.configuration.JournalType;

/**
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
