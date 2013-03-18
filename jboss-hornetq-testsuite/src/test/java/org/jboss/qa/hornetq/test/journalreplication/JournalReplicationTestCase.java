package org.jboss.qa.hornetq.test.journalreplication;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.security.CodeSource;
import java.util.Map;

import javax.ejb.Timeout;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;

import org.hornetq.core.client.impl.ClientConsumerImpl;
import org.hornetq.jms.client.HornetQJMSConnectionFactory;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.clients.SoakProducerClientAck;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.messaging.InspectTools;
import org.jboss.qa.messaging.NetworkProxy;
import org.jboss.qa.tools.ControllableProxy;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.SimpleProxyServer;
import org.jboss.qa.tools.arquillian.extension.RestoreConfig;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author <a href="dpogrebn@redhat.com">Dmytro Pogrebniuk</a>
 * 
 */
@RunWith(Arquillian.class)
public class JournalReplicationTestCase extends HornetQTestCase
{
	
	private static final String  SERVER_LIVE   		= CONTAINER1;
	private static final String  SERVER_BACKUP		= CONTAINER2;
	
	private static final String  SERVER_DIR_LIVE	= JBOSS_HOME_1;
	private static final String  SERVER_DIR_BACKUP	= JBOSS_HOME_2;
	
	private static final String  SERVER_IP_LIVE   	= CONTAINER1_IP;
	private static final String  SERVER_IP_BACKUP 	= CONTAINER2_IP;

	private static final boolean NON_TRANSACTED 	= false; 

	private static final String  CLUSTER_PASSWORD 	= "password";
	
	private static final int     MESSAGING_TO_LIVE_REAL_PORT  	= 5445;
	private static final int     MESSAGING_TO_LIVE_PROXY_PORT	= 51111;
	
	private static final String  NAME_QUEUE              		= "Queue1";
	private static final String  JNDI_QUEUE              		= "queue/InQueue";
	private static final String  NAME_CONNECTION_FACTORY		= "RemoteConnectionFactory";
	private static final String  JNDI_CONNECTION_FACTORY 		= "jms/" + NAME_CONNECTION_FACTORY;
	
	@Test
	@RunAsClient
	public void receiveHalfFromLiveHalfFromBackupTest()
			throws Exception
	{
		resetConfiguration(SERVER_DIR_LIVE);
		resetConfiguration(SERVER_DIR_BACKUP);
		
		int messageNum = 100;
		
		stopServer(SERVER_LIVE);
		deleteDataFolderForJBoss1();
		controller.start(SERVER_LIVE);

		JMSOperations adminLive = this.getJMSOperations(SERVER_LIVE);

		adminLive.setSecurityEnabled(true);
		adminLive.setCheckForLiveServer(true);
		adminLive.setClusterUserPassword(CLUSTER_PASSWORD);
		adminLive.setSharedStore(false);
		adminLive.setPersistenceEnabled(true);

		adminLive.setHaForConnectionFactory				  (NAME_CONNECTION_FACTORY, true);
		adminLive.setFailoverOnShutdown					  (NAME_CONNECTION_FACTORY, true);
		adminLive.setBlockOnAckForConnectionFactory		  (NAME_CONNECTION_FACTORY, true);
		adminLive.setRetryIntervalForConnectionFactory	  (NAME_CONNECTION_FACTORY, 1000L);
		adminLive.setReconnectAttemptsForConnectionFactory(NAME_CONNECTION_FACTORY, 3);
		
		adminLive.createQueue(NAME_QUEUE, JNDI_QUEUE);
		
		adminLive.close();
		stopServer(SERVER_LIVE);

		stopServer(SERVER_BACKUP);
		deleteDataFolderForJBoss2();
		controller.start(SERVER_BACKUP);

		JMSOperations adminBackup = this.getJMSOperations(SERVER_BACKUP);

		adminBackup.setBlockOnAckForConnectionFactory		(NAME_CONNECTION_FACTORY, true);
		adminBackup.setRetryIntervalForConnectionFactory	(NAME_CONNECTION_FACTORY, 1000L);
		adminBackup.setReconnectAttemptsForConnectionFactory(NAME_CONNECTION_FACTORY, 3);
		
		adminBackup.setBackup(true);
		adminBackup.setSecurityEnabled(true);
		adminBackup.setCheckForLiveServer(true);
		adminBackup.setClusterUserPassword(CLUSTER_PASSWORD);
		adminBackup.setSharedStore(false);
		adminBackup.setPersistenceEnabled(true);

		adminBackup.setHaForConnectionFactory				(NAME_CONNECTION_FACTORY, true);
		adminBackup.setFailoverOnShutdown					(NAME_CONNECTION_FACTORY, true);
		
		adminBackup.createQueue(NAME_QUEUE, JNDI_QUEUE);
		
		adminBackup.close();

		stopServer(SERVER_BACKUP);

		SoakProducerClientAck producerToLive = new SoakProducerClientAck(
				SERVER_LIVE, 
				SERVER_IP_LIVE,
				getJNDIPort(), 
				JNDI_QUEUE, 
				messageNum);		
		
		controller.start(SERVER_LIVE);
		
		producerToLive.run();
		
		controller.start(SERVER_BACKUP);		
		
		Thread.sleep(10000);
		
		int recievedNum = 0;

		int maxRecieveRetries = 10;

		Context context = getContext(SERVER_IP_LIVE, getJNDIPort());

		ConnectionFactory cf = (ConnectionFactory) context.lookup(JNDI_CONNECTION_FACTORY);

		Connection conn = cf.createConnection();

		conn.start();

		Queue queue = (Queue) context.lookup(JNDI_QUEUE);

		Session session = conn.createSession(NON_TRANSACTED, Session.CLIENT_ACKNOWLEDGE);

		MessageConsumer receiver = session.createConsumer(queue);

		HornetQJMSConnectionFactory hqCF = (HornetQJMSConnectionFactory) cf;
		System.out.println(hqCF.getServerLocator().getTopology());
		
		boolean wasNotActivated = true;

		while (recievedNum < messageNum)
		{
			Message message = null;
			int numberOfRetries = 0;

			while (numberOfRetries < maxRecieveRetries)
			{
				try
				{
					message = receiver.receive(1000);
					recievedNum++;
					System.out.println("recieved " + recievedNum + " msgs");
					break;
				} catch (JMSException ex)
				{
					numberOfRetries++;
				}
			}
			numberOfRetries = 0;

			if (recievedNum % 10 == 0)
			{
				if (recievedNum > messageNum / 2 && wasNotActivated)
				{
					JBossAS7ServerKillProcessor.kill(SERVER_IP_LIVE);
					Thread.sleep(500);
					wasNotActivated = false;
				}

				while (numberOfRetries < 2)
				{
					try
					{
						message.acknowledge();
						break;

					} catch (JMSException ex)
					{
						numberOfRetries++;
						Thread.sleep(2000);
					}
				}
			}
		}

		assertEquals("Different number sent", messageNum, producerToLive.getCounter());

		assertEquals("Different number received", messageNum, recievedNum);
		
		stopServer(SERVER_LIVE);
		deleteDataFolderForJBoss1();

		stopServer(SERVER_BACKUP);
		deleteDataFolderForJBoss2();
	}

	@SuppressWarnings("unused")
	@Test
	@RunAsClient
	public void networkProblemsWhileInitialReplicationTest() throws Exception
	{
		int messageNum = 100;
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
		
		resetConfiguration(SERVER_DIR_LIVE);
		resetConfiguration(SERVER_DIR_BACKUP);
		
		/*
		 * Preparing live server 
		 */
		controller.stop(SERVER_LIVE);
		deleteDataFolderForJBoss1();
/*		controller.start(SERVER_LIVE);


		JMSOperations adminLive = getJMSOperations(SERVER_LIVE);
		
		adminLive.setSecurityEnabled(true);
		adminLive.setCheckForLiveServer(true);
		adminLive.setClusterUserPassword(CLUSTER_PASSWORD);
		adminLive.setSharedStore(false);
		adminLive.setPersistenceEnabled(true);

		adminLive.setHaForConnectionFactory               	(NAME_CONNECTION_FACTORY, true);
		adminLive.setFailoverOnShutdown                   	(NAME_CONNECTION_FACTORY, true);
		adminLive.setBlockOnAckForConnectionFactory       	(NAME_CONNECTION_FACTORY, false);
		adminLive.setRetryIntervalForConnectionFactory    	(NAME_CONNECTION_FACTORY, 1000L);
		adminLive.setReconnectAttemptsForConnectionFactory	(NAME_CONNECTION_FACTORY, 3);

		adminLive.createQueue(NAME_QUEUE, JNDI_QUEUE);
		
		
		 * In order to create cluster connection to this server use some 
		 * proxy port (in this case MESSAGING_TO_LIVE_PROXY_PORT)
		 

		adminLive.addSocketBinding(
						proxySocketBindingName	= "messaging-via-proxy",
						port					= MESSAGING_TO_LIVE_PROXY_PORT);
		adminLive.createRemoteConnector(
						proxyConnectorName		= "netty-proxy", 
						socketBinding			= proxySocketBindingName, 
						params					= null);
		
		adminLive.removeClusteringGroup("my-cluster");
		adminLive.setClusterConnections(
						clusterName				= "my-cluster", 
						address					= "jms", 
						discoveryGroup			= "dg-group1", 
						forwardWhenNoConsumers	= false, 
						maxHops					= 1, 
						retryInterval			= 1000, 
						useDuplicateDetection	= true, 
						connectorName			= proxyConnectorName);

		adminLive.close();
		
        controller.stop(SERVER_LIVE);
*/
        /*
		 * Preparing backup server 
		 */	
        controller.stop(SERVER_BACKUP);
		deleteDataFolderForJBoss2();
		controller.start(SERVER_BACKUP);

		JMSOperations adminBackup = getJMSOperations(SERVER_BACKUP);

		adminBackup.setBlockOnAckForConnectionFactory		(NAME_CONNECTION_FACTORY, false);
		adminBackup.setRetryIntervalForConnectionFactory	(NAME_CONNECTION_FACTORY, 1000L);
		adminBackup.setReconnectAttemptsForConnectionFactory(NAME_CONNECTION_FACTORY, 3);
		
		adminBackup.setBackup(true);
		adminBackup.setSecurityEnabled(true);
		adminBackup.setCheckForLiveServer(true);
		adminBackup.setClusterUserPassword(CLUSTER_PASSWORD);
		adminBackup.setSharedStore(false);
		adminBackup.setPersistenceEnabled(true);

		adminBackup.setHaForConnectionFactory				(NAME_CONNECTION_FACTORY, true);
		adminBackup.setFailoverOnShutdown					(NAME_CONNECTION_FACTORY, true);

		adminBackup.createQueue(NAME_QUEUE, JNDI_QUEUE);

		adminBackup.close();

		 controller.stop(SERVER_BACKUP);

		/*
		 * Preparing proxy for live server 
		 */
		 final ControllableProxy proxyToLive = new SimpleProxyServer(
				 SERVER_IP_LIVE, 
				 MESSAGING_TO_LIVE_REAL_PORT, 
				 MESSAGING_TO_LIVE_PROXY_PORT);
		 
		proxyToLive.start();

		controller.start(SERVER_LIVE);
		
		/*
		 * Sending messages to live server 
		 */
		SoakProducerClientAck producerToLive = new SoakProducerClientAck(
					SERVER_LIVE, 
					SERVER_IP_LIVE,
					getJNDIPort(), 
					JNDI_QUEUE, 
					messageNum);

		producerToLive.run();

		new Thread(new Runnable()
		{
			
			@Override
			public void run()
			{
				try
				{
					// Give backup some time to start
					Thread.sleep(5000);
					for (int i = 1; i < 10; i++)
					{
							proxyToLive.setBlockCommunicationToServer(true);
							proxyToLive.setBlockCommunicationToClient(true);
							System.out.println("Proxy stopped for 1s");
							Thread.sleep(1000);
							proxyToLive.setBlockCommunicationToServer(false);
							proxyToLive.setBlockCommunicationToClient(false);
							System.out.println("Proxy running for " + i + "s");
							Thread.sleep(i*1000);
					}
				} catch (Exception proxyException)
				{}
			}
		}).start();
		controller.start(SERVER_BACKUP);
		/*
		 * replication start point 
		 */
		System.out.println("Waiting additional " + 60 + "s");		
		Thread.sleep(60000);
		
		/*
		 * Starting retrieving from live.
		 */
		int recievedNum = 0;

		int maxRetries = 10;

		Context context = getContext(SERVER_IP_LIVE, getJNDIPort());

		ConnectionFactory connectionFactory = (ConnectionFactory) context.lookup(JNDI_CONNECTION_FACTORY);

		Connection connection = connectionFactory.createConnection();

		connection.start();

		Queue queue = (Queue) context.lookup(JNDI_QUEUE);

		Session session = connection.createSession(NON_TRANSACTED, Session.CLIENT_ACKNOWLEDGE);

		MessageConsumer receiver = session.createConsumer(queue);
	
		boolean wasNotActivated = true;
		
		while (recievedNum < messageNum)
		{
			Message message = null;
			int numberOfRetries = 0;

			while (numberOfRetries < maxRetries)
			{
				try
				{
					message = receiver.receive(1000);
					
					recievedNum++;
					break;
				} catch (JMSException ex)
				{
					numberOfRetries++;
				}
				
			}
			
			if (message == null)
			{
				break;
			}

			numberOfRetries = 0;

			if (recievedNum % 10 == 0)
			{
				if (recievedNum > messageNum / 2 && wasNotActivated)
				{
					proxyToLive.stop();
					JBossAS7ServerKillProcessor.kill(SERVER_IP_LIVE);
					Thread.sleep(10000);
					wasNotActivated = false;
				}

				while (numberOfRetries < 2)
				{
					try
					{
						message.acknowledge();
						break;

					} catch (JMSException ex)
					{
						numberOfRetries++;
						Thread.sleep(2000);
					}
				}
			}
		}

		assertEquals("Different number sent", messageNum, producerToLive.getCounter());

		assertEquals("Different number received", messageNum, recievedNum);
	}
	
	@Test
	@Ignore
	@RunAsClient
	public void backupHasMessagesTest() throws Exception
	{
		resetConfiguration(SERVER_DIR_LIVE);
		resetConfiguration(SERVER_DIR_BACKUP);
		
		deleteDataFolderForJBoss1();
		//deleteDataFolderForJBoss2();
		
		int messagesNumForLive    = 50;
		int messagesNumForBackup  = 100;
		
		int recievedNumFromLive   = 0;
		int recievedNumFromBackup = 0;
		
		stopServer(SERVER_BACKUP);
		controller.start(SERVER_BACKUP);

		JMSOperations backupAdmin = getJMSOperations(SERVER_BACKUP);

		backupAdmin.setBackup(false);
		
		backupAdmin.setSecurityEnabled(true);

		backupAdmin.setCheckForLiveServer(true);

		backupAdmin.setClusterUserPassword(CLUSTER_PASSWORD);

		backupAdmin.setSharedStore(false);

		backupAdmin.setPersistenceEnabled(true);

		backupAdmin.setHaForConnectionFactory				(NAME_CONNECTION_FACTORY, true);
		backupAdmin.setFailoverOnShutdown					(NAME_CONNECTION_FACTORY, true);
		backupAdmin.setBlockOnAckForConnectionFactory		(NAME_CONNECTION_FACTORY, true);
		backupAdmin.setRetryIntervalForConnectionFactory	(NAME_CONNECTION_FACTORY, 1000L);
		backupAdmin.setReconnectAttemptsForConnectionFactory(NAME_CONNECTION_FACTORY, -1);
		
		backupAdmin.createQueue(NAME_QUEUE, JNDI_QUEUE);
		
		backupAdmin.close();

		stopServer(SERVER_BACKUP);
		
		controller.start(SERVER_BACKUP);
		
		SoakProducerClientAck producerToBackup = new SoakProducerClientAck(
				SERVER_BACKUP, 
				SERVER_IP_BACKUP,
				getJNDIPort(), 
				JNDI_QUEUE, 
				messagesNumForBackup);

		producerToBackup.run();

		stopServer(SERVER_BACKUP);
		controller.start(SERVER_BACKUP);

		backupAdmin = getJMSOperations(SERVER_BACKUP);

		backupAdmin.setBackup(true);
		
		backupAdmin.close();

		stopServer(SERVER_BACKUP);
		
		stopServer(SERVER_LIVE);
		controller.start(SERVER_LIVE);

		JMSOperations liveAdmin = getJMSOperations(SERVER_LIVE);

		liveAdmin.setSecurityEnabled(true);
		liveAdmin.setCheckForLiveServer(true);

		liveAdmin.setClusterUserPassword(CLUSTER_PASSWORD);

		liveAdmin.setSharedStore(false);

		liveAdmin.setPersistenceEnabled(true);

		liveAdmin.setHaForConnectionFactory					(NAME_CONNECTION_FACTORY, true);
		liveAdmin.setFailoverOnShutdown						(NAME_CONNECTION_FACTORY, true);
		liveAdmin.setBlockOnAckForConnectionFactory			(NAME_CONNECTION_FACTORY, true);
		liveAdmin.setRetryIntervalForConnectionFactory		(NAME_CONNECTION_FACTORY, 1000L);
		liveAdmin.setReconnectAttemptsForConnectionFactory	(NAME_CONNECTION_FACTORY, -1);
		
		liveAdmin.createQueue(NAME_QUEUE, JNDI_QUEUE);
		
		liveAdmin.close();
		stopServer(SERVER_LIVE);
		
		controller.start(SERVER_LIVE);
		
		SoakProducerClientAck producerToLive = new SoakProducerClientAck(
				SERVER_LIVE, 
				SERVER_IP_LIVE,
				getJNDIPort(), 
				JNDI_QUEUE, 
				messagesNumForLive);

		producerToLive.run();
		
		controller.start(SERVER_BACKUP);
		
		// waiting for replication to happen
		
		Thread.sleep(5000);
		
		/*
		 * Starting retrieving from live.
		 */


		int maxRetries = 10;

		Context context = getContext(SERVER_IP_LIVE, getJNDIPort());

		ConnectionFactory cf = (ConnectionFactory) context.lookup(JNDI_CONNECTION_FACTORY);

		Connection conn = cf.createConnection();

		conn.start();

		Queue queue = (Queue) context.lookup(JNDI_QUEUE);

		Session session = conn.createSession(NON_TRANSACTED, Session.CLIENT_ACKNOWLEDGE);

		MessageConsumer receiver = session.createConsumer(queue);

		while (recievedNumFromLive < messagesNumForLive)
		{
			Message message = null;
			int numberOfRetries = 0;

			while (numberOfRetries < maxRetries)
			{
				try
				{
					message = receiver.receive(1000);
					recievedNumFromLive++;
					System.out.println("recieved " + recievedNumFromLive + " msgs from live");
					break;
				} catch (JMSException ex)
				{
					numberOfRetries++;
				}
			}
			numberOfRetries = 0;

			if (recievedNumFromLive % 10 == 0)
			{
				while (numberOfRetries < 2)
				{
					try
					{
						message.acknowledge();
						break;

					} catch (JMSException ex)
					{
						numberOfRetries++;
						Thread.sleep(2000);
					}
				}
			}
		}
		
		stopServer(SERVER_LIVE);
		
		backupAdmin = getJMSOperations(SERVER_BACKUP);

		backupAdmin.setBackup(false);
		
		backupAdmin.close();
		
		stopServer(SERVER_BACKUP);
		
		controller.start(SERVER_BACKUP);
		
		/*
		 * Starting retrieving from backup.
		 */
		maxRetries = 10;

		context = getContext(SERVER_IP_BACKUP, getJNDIPort());

		cf = (ConnectionFactory) context.lookup(JNDI_CONNECTION_FACTORY);

		conn = cf.createConnection();

		conn.start();

		queue = (Queue) context.lookup(JNDI_QUEUE);

		session = conn.createSession(NON_TRANSACTED, Session.CLIENT_ACKNOWLEDGE);

		receiver = session.createConsumer(queue);

		while (recievedNumFromBackup < messagesNumForBackup)
		{
			Message message = null;
			int numberOfRetries = 0;

			while (numberOfRetries < maxRetries)
			{
				try
				{
					message = receiver.receive(1000);
					if (message == null)
					{
						throw new Exception("Expected message was not received.");
					}
					recievedNumFromBackup++;
					System.out.println("recieved " + recievedNumFromBackup + " msgs from backup");
					break;
				} catch (JMSException ex)
				{
					numberOfRetries++;
				}
			}
			numberOfRetries = 0;

			if (recievedNumFromBackup % 10 == 0)
			{
				while (numberOfRetries < 2)
				{
					try
					{
						message.acknowledge();
						break;

					} catch (JMSException ex)
					{
						numberOfRetries++;
						Thread.sleep(2000);
					}
				}
			}
		}

		assertEquals("Different number processed from live", messagesNumForLive, recievedNumFromLive);

		assertEquals("Different number processed from backup", messagesNumForBackup, recievedNumFromBackup);
	}
	
	private void resetConfiguration(String directory) throws IOException
	{
		if (directory == null || directory.trim().isEmpty()){
			throw new IllegalArgumentException("Invalid server home:[" + directory + "]");
		}
		
		File serverHome = new File(directory);
		if (!serverHome.exists() || !serverHome.isDirectory()){
			throw new IllegalArgumentException("Directory does not exist:[" + directory + "]");
		}
		
		File confDirectory = new File(serverHome, "standalone" + File.separator + "configuration");
		
		File defaultConfiguration = new File(
				confDirectory,
				"standalone-full-ha.xml.backup");
		
		File actualConfiguration = new File(
				confDirectory,
				"standalone-full-ha.xml");
		
		actualConfiguration.delete();
		
		RestoreConfig restorator = new RestoreConfig();
		restorator.copyFile(defaultConfiguration, actualConfiguration);
	}
}
