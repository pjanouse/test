package org.jboss.qa.hornetq.test.journalreplication;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.test.journalreplication.configuration.AddressFullPolicy;
import org.jboss.qa.hornetq.test.journalreplication.configuration.JournalReplicationConfiguration;
import org.jboss.qa.hornetq.test.journalreplication.configuration.JournalType;
import org.jboss.qa.hornetq.test.journalreplication.utils.JMSUtil;
import org.jboss.qa.hornetq.test.journalreplication.utils.NetworkProblemController;
import org.jboss.qa.hornetq.test.journalreplication.utils.ServerUtil;
import org.jboss.qa.hornetq.test.journalreplication.utils.ThreadUtil;
import org.jboss.qa.hornetq.tools.ControllableProxy;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.Message;
import javax.jms.MessageConsumer;
import java.rmi.RemoteException;
import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="dpogrebn@redhat.com">Dmytro Pogrebniuk</a>
 * 
 */
@RunWith(Arquillian.class)
public abstract class JournalReplicationAbstract extends HornetQTestCase
{
	private static final Logger log = Logger.getLogger(JournalReplicationAbstract.class);
	private JournalReplicationConfiguration preparator;

	private static final int MESSAGES_NUM = 100;
	
	private static final int ACKNOWLEDGE_EVERY = 10;
	
	private static final int RETRY_MAX_ATTEMPTS = 3;
	private static final int RETRY_SLEEP_SECS = 2;
	
	enum NetworkFailurePoint
	{
		NONE, INITIAL_REPLICATION, POST_INITIAL_REPLICATION
	}
	
	@Before
	public void beforeEachTest()
	{
		preparator = new JournalReplicationConfiguration(controller);
		
		preparator.prepareLive(container(1), this);
		
		preparator.prepareBackup(container(2));
	}

    @After
    public void stopServer()    {
        stopServer(CONTAINER1_NAME);
        stopServer(CONTAINER2_NAME);
    }

	@Test/*(timeout=180000) = 3 minutes see https://issues.jboss.org/browse/ARQ-1071*/
	@RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
	public void journalReplicationWithoutNetworkProblemTest() throws Exception
	{
		testCore(NetworkFailurePoint.NONE);
	}

	
	@Test/*(timeout=180000) = 3 minutes see https://issues.jboss.org/browse/ARQ-1071*/
	@RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
	public void networkProblemsWhileInitialReplicationTest() throws Exception
	{
		testCore(NetworkFailurePoint.INITIAL_REPLICATION);
	}
	
	@Test/*(timeout=180000) = 3 minutes see https://issues.jboss.org/browse/ARQ-1071*/
	@RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
	public void networkProblemsAfterInitialReplicationTest() throws Exception
	{
		testCore(NetworkFailurePoint.POST_INITIAL_REPLICATION);
	}

	public void testCore(NetworkFailurePoint testPoint) throws RemoteException
	{
		ControllableProxy proxyToLive = preparator.createProxyToLive();
		proxyToLive.start();

        ControllableProxy proxyToBackup = preparator.createProxyToBackup();
        proxyToBackup.start();

		startLiveServer();

		sendMessagesToLive();

		if (testPoint == NetworkFailurePoint.INITIAL_REPLICATION)
		{
			// random 4-6
			int initialDelay = new Random().nextInt(2) + 4;
			new NetworkProblemController(proxyToLive,initialDelay).start();
            new NetworkProblemController(proxyToBackup,initialDelay).start();
		}
		
		startBackupServer();
		
		/*
		 * replication start point and network failures
		 */
		log.info("Waiting additional " + 60 + " s");
		sleepSeconds(60);

		MessageConsumer receiver = preparator.createConsumerForLive();

		if (testPoint == NetworkFailurePoint.POST_INITIAL_REPLICATION)
		{
			// random 1-3
			int initialDelay = new Random().nextInt(2) + 1;
			new NetworkProblemController(proxyToLive,initialDelay).start();
            new NetworkProblemController(proxyToBackup,initialDelay).start();
		}
		
		boolean isKillTrigered = false;
		int messagesRecievedNum = 0;
		int messagesAcknowledgedNum = 0;

		while (messagesRecievedNum < MESSAGES_NUM)
		{
			Message message = receiveMessage(receiver, RETRY_MAX_ATTEMPTS, RETRY_SLEEP_SECS);

			if (message == null)
			{
				log.info("Got null message. Breaking...");
				break;
			} else
			{
				messagesRecievedNum++;
				log.info("Received ["+messagesRecievedNum+"] messages...");				
			}

			if (messagesRecievedNum % ACKNOWLEDGE_EVERY == 0)
			{
				if (messagesRecievedNum > MESSAGES_NUM / 2 && !isKillTrigered)
				{
					proxyToLive.stop();
                    proxyToBackup.stop();

                    killServer(CONTAINER1_NAME);

					isKillTrigered = true;

					sleepSeconds(10);
				}

				boolean isAcknowledged = acknowlegeMessage(message, RETRY_MAX_ATTEMPTS,RETRY_SLEEP_SECS);

				if (!isAcknowledged)
				{
					log.error("Messages were not acknowledged. Breaking...");
					break;
				}
				messagesAcknowledgedNum += ACKNOWLEDGE_EVERY;
			}
		}

		assertEquals("Incorrect number received:", MESSAGES_NUM, messagesAcknowledgedNum);

	}

	private void startLiveServer()
	{
		ServerUtil.startServer(controller, preparator.getLiveServerID());
	}
	
	private void startBackupServer()
	{
		ServerUtil.startServer(controller, preparator.getBackupServerID());
	}


	private void killLiveServer()
	{
		ServerUtil.killServer(preparator.getLiveServerIP());
	}
	
	private void sleepSeconds(int seconds)
	{
		ThreadUtil.sleepSeconds(seconds);
	}
	
	private void sendMessagesToLive()
	{
//		SoakProducerClientAck producerToLive = preparator.createSenderToLive(MESSAGES_NUM);
//
//		producerToLive.run();

        ProducerTransAck p = preparator.createSenderToLive(MESSAGES_NUM);
        p.setMessageBuilder(new TextMessageBuilder(300 * 1024));
        p.start();
	}
	
	private Message receiveMessage(MessageConsumer receiver, int maxRetryNum, int retrySleepSeconds)
	{
		return JMSUtil.receiveMessage(receiver, maxRetryNum, retrySleepSeconds);
	}
	
	private boolean acknowlegeMessage(Message message, int maxRetryNum, int retrySleepSeconds)
	{
		return JMSUtil.acknowlegeMessage(message, maxRetryNum, retrySleepSeconds);
	}
	
	public abstract JournalType getJournalType();
	public abstract AddressFullPolicy getAddressFullPolicy();
}