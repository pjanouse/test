package org.jboss.qa.hornetq.test.journalreplication.utils;

import org.jboss.logging.Logger;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;

/**
 * @author <a href="dpogrebn@redhat.com">Dmytro Pogrebniuk</a>
 * 
 */
public class JMSUtil
{
	private static final Logger log = Logger.getLogger(JMSUtil.class);

	/**
	 * Receives message from producer with retries.
	 * 
	 * @param receiver
	 *            the consumer to receive from.
	 * @param maxRetryNum
	 *            maximum possible retries
	 * @param retrySleepSeconds
	 *            time to sleep on retry
	 *            
	 * @return the next message produced for this message consumer, or null if
	 *         the timeout expires or this message consumer is concurrently
	 *         closed
	 */
	public static Message receiveMessage(
			MessageConsumer receiver, 
			int maxRetryNum, 
			int retrySleepSeconds)
	{
		log.info("Receiving message...");

		Message message = null;

		int numberOfReceivingRetries = 0;

		while (numberOfReceivingRetries < maxRetryNum)
		{
			try
			{
				message = receiver.receive(10000);

				break;
			} catch (JMSException receivingException)
			{
				log.error("Exception while receiving", receivingException);

				numberOfReceivingRetries++;

				sleepSeconds(retrySleepSeconds);
			}
		}
		return message;
	}

	/**
	 * Acknowledges specified message with retry.
	 * 
	 * @param message
	 *            message to acknowledge
	 * @param maxRetryNum
	 *            maximum retry numbers
	 * @param retrySleepSeconds
	 * 			  time to sleep on retry
	 * 
	 * @return <code>true</code> - if ack was successful, <code>false</code> -
	 *         otherwise
	 */
	public static boolean acknowlegeMessage(Message message, int maxRetryNum, int retrySleepSeconds)
	{

		log.info("Acknowledging message...");
		int numberOfAckRetries = 0;

		while (numberOfAckRetries < maxRetryNum)
		{
			try
			{
				message.acknowledge();

				return true;

			} catch (JMSException acknowledgeException)
			{
				log.error("Exception while acknowledging", acknowledgeException);

				numberOfAckRetries++;

				sleepSeconds(retrySleepSeconds);
			}
		}
		return false;
	}

	private static void sleepSeconds(int seconds)
	{
		ThreadUtil.sleepSeconds(seconds);
	}

}
