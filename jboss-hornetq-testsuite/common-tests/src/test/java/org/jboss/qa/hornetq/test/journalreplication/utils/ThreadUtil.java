package org.jboss.qa.hornetq.test.journalreplication.utils;

import org.jboss.logging.Logger;

/**
 * @author <a href="dpogrebn@redhat.com">Dmytro Pogrebniuk</a>
 *
 */
public class ThreadUtil
{
	private static final Logger log = Logger.getLogger(ThreadUtil.class);
	
	/**
	 * Thread.sleep with exception handling.
	 * 
	 * @param milisecs
	 *            the length of time to sleep in milliseconds.
	 * 
	 */
	public static void sleepSeconds(int seconds)
	{
		try
		{
			Thread.sleep(seconds * 1000);
		} catch (InterruptedException interuptedException)
		{
			log.error("Sleep-thread was interupted", interuptedException);
		}

	}

}
