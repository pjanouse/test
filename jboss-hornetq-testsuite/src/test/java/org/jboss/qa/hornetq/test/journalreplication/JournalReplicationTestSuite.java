package org.jboss.qa.hornetq.test.journalreplication;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * 
 * @see <a href="https://issues.jboss.org/browse/ARQ-567">ARQ-567</a>
 * 
 * @author <a href="dpogrebn@redhat.com">Dmytro Pogrebniuk</a>
 *
 */
@RunWith(Suite.class)
@SuiteClasses({ 
	JournalReplicationAsyncioBlockTestCase.class, 
	JournalReplicationAsyncioPageTestCase.class,
	JournalReplicationNioBlockTestCase.class, 
	JournalReplicationNioPageTestCase.class 
})
public class JournalReplicationTestSuite
{

}
