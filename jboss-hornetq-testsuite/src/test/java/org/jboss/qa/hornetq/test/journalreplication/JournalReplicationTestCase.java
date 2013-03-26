package org.jboss.qa.hornetq.test.journalreplication;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.clients.SoakProducerClientAck;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.ControllableProxy;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.SimpleProxyServer;
import org.jboss.qa.tools.arquillian.extension.RestoreConfig;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author <a href="dpogrebn@redhat.com">Dmytro Pogrebniuk</a>
 * 
 */
@RunWith(Arquillian.class)
public class JournalReplicationTestCase extends HornetQTestCase
{
	private static final Logger log = Logger.getLogger(JournalReplicationTestCase.class);

	private static final String SERVER_LIVE = CONTAINER1;
	private static final String SERVER_BACKUP = CONTAINER2;

	private static final String SERVER_DIR_LIVE = JBOSS_HOME_1;
	private static final String SERVER_DIR_BACKUP = JBOSS_HOME_2;

	private static final String SERVER_IP_LIVE = CONTAINER1_IP;

	private static final boolean NON_TRANSACTED = false;

	private static final String CLUSTER_PASSWORD = "password";

	private static final int MESSAGING_TO_LIVE_REAL_PORT = 5445;
	private static final int MESSAGING_TO_LIVE_PROXY_PORT = 51111;

	private static final String NAME_QUEUE = "Queue1";
	private static final String JNDI_QUEUE = "queue/InQueue";
	private static final String NAME_CONNECTION_FACTORY = "RemoteConnectionFactory";
	private static final String JNDI_CONNECTION_FACTORY = "jms/" + NAME_CONNECTION_FACTORY;

	private static final int MAX_RETRIES = 3;
	private static final int RETRY_SLEEP_SECS = 2;
	private static final int MESSAGES_NUM = 100;

	/*
	 * 
	 */
	String clusterName = null;
	String address = null;
	String discoveryGroup = null;
	boolean forwardWhenNoConsumers = false;
	int maxHops = -1;
	int retryInterval = -1;
	boolean useDuplicateDetection = false;
	String connectorName = null;
	String proxyConnectorName = null;
	String socketBinding = null;
	Map<String, String> params = null;
	String proxySocketBindingName = "messaging-via-proxy";
	int port = -1;
	/*
	 * 
	 */

	@Test
	@RunAsClient
	public void networkProblemsWhileInitialReplicationTest() throws Exception
	{
		prepareLive();
		prepareBackup();

		ControllableProxy proxyToLive = new SimpleProxyServer(
				SERVER_IP_LIVE, 
				MESSAGING_TO_LIVE_REAL_PORT,
				MESSAGING_TO_LIVE_PROXY_PORT);

		proxyToLive.start();

		controller.start(SERVER_LIVE);

		SoakProducerClientAck producerToLive = new SoakProducerClientAck(
				SERVER_LIVE, 
				SERVER_IP_LIVE, 
				getJNDIPort(),
				JNDI_QUEUE, 
				MESSAGES_NUM);

		producerToLive.run();

		new Thread(new NetworkProblemRunnable(proxyToLive)).start();
		
		controller.start(SERVER_BACKUP);
		
		//replication start point
		
		log.info("Waiting additional " + 60 + " s");

		sleepSeconds(60);

		// Starting retrieving from live.
		
		Context context = getContext(SERVER_IP_LIVE, getJNDIPort());

		ConnectionFactory connectionFactory = (ConnectionFactory) context.lookup(JNDI_CONNECTION_FACTORY);

		Connection connection = connectionFactory.createConnection();

		connection.start();

		Queue queue = (Queue) context.lookup(JNDI_QUEUE);

		Session session = connection.createSession(NON_TRANSACTED, Session.CLIENT_ACKNOWLEDGE);

		MessageConsumer receiver = session.createConsumer(queue);

		boolean isKillTrigered = false;
		int messagesRecievedNum = 0;

		while (messagesRecievedNum < MESSAGES_NUM)
		{
			Message message = receiveMessage(receiver, MAX_RETRIES);

			if (message == null)
			{
				log.info("Got null message. Breaking...");
				break;
			} else
			{
				messagesRecievedNum++;
			}

			if (messagesRecievedNum % 10 == 0)
			{
				if (messagesRecievedNum > MESSAGES_NUM / 2 && !isKillTrigered)
				{
					proxyToLive.stop();

					killServer(SERVER_IP_LIVE);

					isKillTrigered = true;

					sleepSeconds(10);
				}

				boolean isAcknowledged = acknowlegeMessage(message, MAX_RETRIES);

				if (!isAcknowledged)
				{
					log.error("Messages were not acknowledged. Breaking...");
					break;
				}
			}
		}

		assertEquals("Incorrect number sent:", MESSAGES_NUM, producerToLive.getCounter());

		assertEquals("Incorrect number received:", MESSAGES_NUM, messagesRecievedNum);
	}

	/**
	 * Receives message from producer with retries.
	 * 
	 * @param receiver
	 *            the consumer to receive from.
	 * @param maxRetryNum
	 *            maximum possible retries
	 * @return the next message produced for this message consumer, or null if
	 *         the timeout expires or this message consumer is concurrently
	 *         closed
	 */
	private Message receiveMessage(MessageConsumer receiver, int maxRetryNum)
	{
		Message message = null;

		int numberOfReceivingRetries = 0;

		while (numberOfReceivingRetries < maxRetryNum)
		{
			try
			{
				message = receiver.receive(1000);

				break;
			} catch (JMSException receivingException)
			{
				log.error("Exception while receiving", receivingException);

				numberOfReceivingRetries++;

				sleepSeconds(RETRY_SLEEP_SECS);
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
	 * 
	 * @return <code>true</code> - if ack was successful, <code>false</code> -
	 *         otherwise
	 */
	private boolean acknowlegeMessage(Message message, int maxRetryNum)
	{
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

				sleepSeconds(RETRY_SLEEP_SECS);
			}
		}
		return false;
	}

	/**
	 * Thread.sleep with exception handling.
	 * 
	 * @param milisecs
	 *            the length of time to sleep in milliseconds.
	 * 
	 */
	private void sleepSeconds(long seconds)
	{
		try
		{
			Thread.sleep(seconds * 1000);
		} catch (InterruptedException interuptedException)
		{
			log.error("Sleep-thread was interupted", interuptedException);
		}

	}
	
	protected void killServer(String ipAddress)
	{
		try
		{
			JBossAS7ServerKillProcessor.kill(ipAddress);
		} catch (Exception killingException)
		{
			log.error("Exception while killing server ip:[" + ipAddress + "]", 
					  killingException);
		}
	}
			

	private void prepareLive()
	{
		resetConfiguration(SERVER_DIR_LIVE);

		controller.stop(SERVER_LIVE);
		deleteDataFolderForJBoss1();

		controller.start(SERVER_LIVE);

		JMSOperations adminLive = getJMSOperations(SERVER_LIVE);

		adminLive.setSecurityEnabled(true);
		adminLive.setCheckForLiveServer(true);
		adminLive.setClusterUserPassword(CLUSTER_PASSWORD);
		adminLive.setSharedStore(false);
		adminLive.setPersistenceEnabled(true);

		adminLive.setHaForConnectionFactory(NAME_CONNECTION_FACTORY, true);
		adminLive.setFailoverOnShutdown(NAME_CONNECTION_FACTORY, true);
		adminLive.setBlockOnAckForConnectionFactory(NAME_CONNECTION_FACTORY, false);
		adminLive.setRetryIntervalForConnectionFactory(NAME_CONNECTION_FACTORY, 1000L);
		adminLive.setReconnectAttemptsForConnectionFactory(NAME_CONNECTION_FACTORY, 3);

		adminLive.createQueue(NAME_QUEUE, JNDI_QUEUE);

		adminLive.addSocketBinding("bindname", "234.255.10.1", 55234);

		adminLive.addSocketBinding(proxySocketBindingName = "messaging-via-proxy", port = MESSAGING_TO_LIVE_PROXY_PORT);

		adminLive.createRemoteConnector(proxyConnectorName = "netty-proxy", socketBinding = proxySocketBindingName,
				params = null);

		adminLive.removeClusteringGroup("my-cluster");
		adminLive.setClusterConnections(clusterName = "my-cluster", address = "jms", discoveryGroup = "dg-group1",
				forwardWhenNoConsumers = false, maxHops = 1, retryInterval = 1000, useDuplicateDetection = true,
				connectorName = proxyConnectorName);

		adminLive.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
		adminLive.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", true);
		adminLive.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", true);
		adminLive.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", true);
		adminLive.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", true);
		adminLive.setPermissionToRoleToSecuritySettings("#", "guest", "manage", true);
		adminLive.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);

		adminLive.close();

		controller.stop(SERVER_LIVE);
	}
	
	private void prepareBackup()
	{
		resetConfiguration(SERVER_DIR_BACKUP);
		controller.stop(SERVER_BACKUP);
		deleteDataFolderForJBoss2();
		controller.start(SERVER_BACKUP);

		JMSOperations adminBackup = getJMSOperations(SERVER_BACKUP);

		adminBackup.setBlockOnAckForConnectionFactory(NAME_CONNECTION_FACTORY, false);
		adminBackup.setRetryIntervalForConnectionFactory(NAME_CONNECTION_FACTORY, 1000L);
		adminBackup.setReconnectAttemptsForConnectionFactory(NAME_CONNECTION_FACTORY, 3);

		adminBackup.setBackup(true);
		adminBackup.setSecurityEnabled(true);
		adminBackup.setCheckForLiveServer(true);
		adminBackup.setClusterUserPassword(CLUSTER_PASSWORD);
		adminBackup.setSharedStore(false);
		adminBackup.setPersistenceEnabled(true);

		adminBackup.setHaForConnectionFactory(NAME_CONNECTION_FACTORY, true);
		adminBackup.setFailoverOnShutdown(NAME_CONNECTION_FACTORY, true);

		adminBackup.createQueue(NAME_QUEUE, JNDI_QUEUE);

		adminBackup.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
		adminBackup.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", true);
		adminBackup.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", true);
		adminBackup.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", true);
		adminBackup.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", true);
		adminBackup.setPermissionToRoleToSecuritySettings("#", "guest", "manage", true);
		adminBackup.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);

		adminBackup.close();

		controller.stop(SERVER_BACKUP);
	}

	/**
	 * Restores working configuration from 'backup' file.
	 * 
	 * @param serverDirectory
	 *            JBossHome for specific server instance.
	 */
	private void resetConfiguration(String serverDirectory)
	{
		if (serverDirectory == null || serverDirectory.trim().isEmpty())
		{
			throw new IllegalArgumentException("Invalid server home:[" + serverDirectory + "]");
		}

		File serverHome = new File(serverDirectory);
		if (!serverHome.exists() || !serverHome.isDirectory())
		{
			throw new IllegalArgumentException("Directory does not exist:[" + serverDirectory + "]");
		}

		File confDirectory = new File(serverHome, "standalone" + File.separator + "configuration");

		File defaultConfiguration = new File(confDirectory, "standalone-full-ha.xml.backup");

		File actualConfiguration = new File(confDirectory, "standalone-full-ha.xml");

		actualConfiguration.delete();

		RestoreConfig restorator = new RestoreConfig();

		try
		{
			restorator.copyFile(defaultConfiguration, actualConfiguration);
		} catch (IOException copyException)
		{
			throw new RuntimeException(copyException);
		}
	}
	
	private class NetworkProblemRunnable implements Runnable
	{
		private ControllableProxy proxy;
		
		public NetworkProblemRunnable(ControllableProxy proxy)
		{
			this.proxy = proxy;
		}
		
		@Override
		public void run()
		{
			// initial delay
			sleepSeconds(5);
			
			for (int i = 1; i < 10; i++)
			{
				blockCommunication(true);

				log.info("Proxy stopped: 1s");

				sleepSeconds(1);

				blockCommunication(false);

				log.info("Proxy is working: " + i + "s");

				sleepSeconds(i);
			}
		}
		
		private void blockCommunication(boolean isBlock)
		{
			try
			{
				proxy.setBlockCommunicationToServer(isBlock);
				proxy.setBlockCommunicationToClient(isBlock);
			} catch (RemoteException proxyException)
			{
				log.warn("Proxy has thrown exception", proxyException);
			}
		}
	}
}