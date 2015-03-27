package org.jboss.qa.hornetq.test.journalreplication;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
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
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.SimpleProxyServer;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.*;
import javax.naming.Context;
import java.io.File;
import java.rmi.RemoteException;
import java.util.Map;
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

	private static final int MESSAGES_NUM = 100;
	
	private static final int ACKNOWLEDGE_EVERY = 10;
	
	private static final int RETRY_MAX_ATTEMPTS = 3;
	private static final int RETRY_SLEEP_SECS = 2;


	public abstract JournalType getJournalType();
	public abstract AddressFullPolicy getAddressFullPolicy();

	private static final String CLUSTER_PASSWORD = "password";
	private static final String NAME_QUEUE = "Queue1";

	private static final String JNDI_QUEUE = "queue/InQueue";
	private static final String NAME_CONNECTION_FACTORY = "RemoteConnectionFactory";
	private static final String JNDI_CONNECTION_FACTORY = "jms/" + NAME_CONNECTION_FACTORY;

	private static final String BACKUP_GROUP_NAME = "backup-group1";

	private final String SERVER_DIR_LIVE = container(1).getServerHome();
	private final String SERVER_DIR_BACKUP = container(2).getServerHome();

	private final String SERVER_LIVE = container(1).getName();
	private final String SERVER_BACKUP = container(2).getName();

	private final String SERVER_IP_LIVE = container(1).getHostname();
	private final String SERVER_IP_BACKUP = container(2).getHostname();

	private  final int MESSAGING_TO_LIVE_REAL_PORT = container(1).getHornetqPort();
	private  final int MESSAGING_TO_BACKUP_REAL_PORT = container(2).getHornetqPort();
	private static final int MESSAGING_TO_LIVE_PROXY_PORT = 51111;
	private static final int MESSAGING_TO_BACKUP_PROXY_PORT = 51112;

	private static final boolean NON_TRANSACTED = false;

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
	String host = "localhost";


	enum NetworkFailurePoint
	{
		NONE, INITIAL_REPLICATION, POST_INITIAL_REPLICATION
	}
	
	@Before
	public void beforeEachTest() throws Exception
	{
		prepareLive(container(1));
		
		prepareBackup(container(2));
	}

    @After
    public void stopServer()    {
        container(1).stop();
        container(2).stop();
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
		ControllableProxy proxyToLive = createProxyToLive();
		proxyToLive.start();

        ControllableProxy proxyToBackup = createProxyToBackup();
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

		MessageConsumer receiver = createConsumerForLive();

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

                    container(1).kill();

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
        container(1).start();
	}
	
	private void startBackupServer()
	{
        container(2).start();
	}


	private void killLiveServer()
	{
		ServerUtil.killServer(getLiveServerIP());
	}
	
	private void sleepSeconds(int seconds)
	{
		ThreadUtil.sleepSeconds(seconds);
	}
	
	private void sendMessagesToLive()
	{
//		SoakProducerClientAck producerToLive = createSenderToLive(MESSAGES_NUM);
//
//		producerToLive.run();

        ProducerTransAck p = createSenderToLive(MESSAGES_NUM);
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

	public void prepareLive(Container liveServer)  throws Exception
	{
		String broadCastGroupName = "bg-group1";
		String discoveryGroupName = "dg-group1";
		String messagingGroupSocketBindingName = "messaging-group";
		String clusterGroupName = "my-cluster";

		liveServer.start();

		JMSOperations adminLive = liveServer.getJmsOperations();

		adminLive.setJournalType(getJournalType().toString());

		adminLive.removeAddressSettings("#");

		adminLive.addAddressSettings(
				"#",
				getAddressFullPolicy().toString(),
				10485760,
				0,
				10485760,
				1048570);

		adminLive.setSecurityEnabled(true);
		adminLive.setCheckForLiveServer(true);
		adminLive.setClusterUserPassword(CLUSTER_PASSWORD);
		adminLive.setSharedStore(false);
		adminLive.setPersistenceEnabled(true);
		adminLive.setBackupGroupName(BACKUP_GROUP_NAME);

		adminLive.createQueue(NAME_QUEUE, JNDI_QUEUE);

		adminLive.addSocketBinding("bindname", "234.255.10.1", 55234);

		adminLive.addRemoteSocketBinding(
				proxySocketBindingName = "messaging-via-proxy",
				host = "localhost",
				port = MESSAGING_TO_LIVE_PROXY_PORT);

		adminLive.createRemoteConnector(
				proxyConnectorName = "netty-proxy",
				socketBinding = proxySocketBindingName,
				params = null);

		adminLive.setHaForConnectionFactory(NAME_CONNECTION_FACTORY, true);
		adminLive.setFailoverOnShutdown(NAME_CONNECTION_FACTORY, true);
		adminLive.setBlockOnAckForConnectionFactory(NAME_CONNECTION_FACTORY, false);
		adminLive.setRetryIntervalForConnectionFactory(NAME_CONNECTION_FACTORY, 1000L);
		adminLive.setReconnectAttemptsForConnectionFactory(NAME_CONNECTION_FACTORY, -1);
		//adminLive.setConnectorOnConnectionFactory(NAME_CONNECTION_FACTORY, proxyConnectorName);

		adminLive.removeClusteringGroup(clusterGroupName);
		adminLive.setClusterConnections(
				clusterName = clusterGroupName,
				address = "jms",
				discoveryGroup = discoveryGroupName,
				forwardWhenNoConsumers = false,
				maxHops = 1,
				retryInterval = 1000,
				useDuplicateDetection = true,
//				connectorName = proxyConnectorName);
				connectorName = "netty");
		adminLive.removeBroadcastGroup(broadCastGroupName);
		adminLive.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, proxyConnectorName, "");

		adminLive.removeDiscoveryGroup(discoveryGroupName);
		adminLive.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

		adminLive.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
		adminLive.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", true);
		adminLive.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", true);
		adminLive.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", true);
		adminLive.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", true);
		adminLive.setPermissionToRoleToSecuritySettings("#", "guest", "manage", true);
		adminLive.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);

		adminLive.close();

		liveServer.stop();

		File applicationUsersModified = new File(
				"src" + File.separator +
						"test" + File.separator +
						"resources" + File.separator +
						"org" + File.separator +
						"jboss" + File.separator +
						"qa" + File.separator +
						"hornetq" + File.separator +
						"test" + File.separator +
						"security" + File.separator +
						"application-users.properties");
		File applicationUsersOriginal = new File(
				SERVER_DIR_LIVE + File.separator +
						"standalone" + File.separator +
						"configuration" + File.separator +
						"application-users.properties");

		FileUtils.copyFile(applicationUsersModified, applicationUsersOriginal);

		File applicationRolesModified = new File(
				"src" + File.separator +
						"test" + File.separator +
						"resources" + File.separator +
						"org" + File.separator +
						"jboss" + File.separator +
						"qa" + File.separator +
						"hornetq" + File.separator +
						"test" + File.separator +
						"security" + File.separator +
						"application-roles.properties");
		File applicationRolesOriginal = new File(
				SERVER_DIR_LIVE + File.separator +
						"standalone" + File.separator +
						"configuration" + File.separator +
						"application-roles.properties");

		FileUtils.copyFile(applicationRolesModified, applicationRolesOriginal);
	}

	public void prepareBackup(Container backupServer) throws Exception
	{

		String broadCastGroupName = "bg-group1";
		String discoveryGroupName = "dg-group1";
		String messagingGroupSocketBindingName = "messaging-group";
		String clusterGroupName = "my-cluster";

		backupServer.start();

		JMSOperations adminBackup = backupServer.getJmsOperations();

		adminBackup.setBlockOnAckForConnectionFactory(NAME_CONNECTION_FACTORY, false);
		adminBackup.setRetryIntervalForConnectionFactory(NAME_CONNECTION_FACTORY, 1000L);
		adminBackup.setReconnectAttemptsForConnectionFactory(NAME_CONNECTION_FACTORY, -1);

		adminBackup.setBackup(true);
		adminBackup.setSecurityEnabled(true);
		adminBackup.setCheckForLiveServer(true);
		adminBackup.setClusterUserPassword(CLUSTER_PASSWORD);
		adminBackup.setSharedStore(false);
		adminBackup.setPersistenceEnabled(true);
		adminBackup.setBackupGroupName(BACKUP_GROUP_NAME);

		adminBackup.setHaForConnectionFactory(NAME_CONNECTION_FACTORY, true);
		adminBackup.setFailoverOnShutdown(NAME_CONNECTION_FACTORY, true);

		adminBackup.addRemoteSocketBinding(
				proxySocketBindingName = "messaging-via-proxy",
				host = "localhost",
				port = MESSAGING_TO_BACKUP_PROXY_PORT);

		adminBackup.createRemoteConnector(
				proxyConnectorName = "netty-proxy",
				socketBinding = proxySocketBindingName,
				params = null);

		adminBackup.removeClusteringGroup(clusterGroupName);
		adminBackup.setClusterConnections(
				clusterName = clusterGroupName,
				address = "jms",
				discoveryGroup = discoveryGroupName,
				forwardWhenNoConsumers = false,
				maxHops = 1,
				retryInterval = 1000,
				useDuplicateDetection = true,
//                connectorName = proxyConnectorName);
				connectorName = "netty");

		adminBackup.removeBroadcastGroup(broadCastGroupName);
		adminBackup.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, proxyConnectorName, "");

		adminBackup.removeDiscoveryGroup(discoveryGroupName);
		adminBackup.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

		adminBackup.createQueue(NAME_QUEUE, JNDI_QUEUE);

		adminBackup.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
		adminBackup.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", true);
		adminBackup.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", true);
		adminBackup.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", true);
		adminBackup.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", true);
		adminBackup.setPermissionToRoleToSecuritySettings("#", "guest", "manage", true);
		adminBackup.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);

		adminBackup.close();

		backupServer.stop();

		File applicationUsersModified = new File(
				"src" + File.separator +
						"test" + File.separator +
						"resources" + File.separator +
						"org" + File.separator +
						"jboss" + File.separator +
						"qa" + File.separator +
						"hornetq" + File.separator +
						"test" + File.separator +
						"security" + File.separator +
						"application-users.properties");
		File applicationUsersOriginal = new File(
				SERVER_DIR_BACKUP + File.separator +
						"standalone" + File.separator +
						"configuration" + File.separator +
						"application-users.properties");

		FileUtils.copyFile(applicationUsersModified, applicationUsersOriginal);

		File applicationRolesModified = new File(
				"src" + File.separator +
						"test" + File.separator +
						"resources" + File.separator +
						"org" + File.separator +
						"jboss" + File.separator +
						"qa" + File.separator +
						"hornetq" + File.separator +
						"test" + File.separator +
						"security" + File.separator +
						"application-roles.properties");
		File applicationRolesOriginal = new File(
				SERVER_DIR_BACKUP + File.separator +
						"standalone" + File.separator +
						"configuration" + File.separator +
						"application-roles.properties");

		FileUtils.copyFile(applicationRolesModified, applicationRolesOriginal);

	}

	public String getLiveServerID()
	{
		return SERVER_LIVE;
	}

	public String getLiveServerIP()
	{
		return SERVER_IP_LIVE;
	}


	public String getBackupServerID()
	{
		return SERVER_BACKUP;
	}

	public void setJournalType(String name)
	{
		// TODO Auto-generated method stub

	}

	public MessageConsumer createConsumerForLive()
	{
		try
		{
			Context context = container(1).getContext();

			ConnectionFactory connectionFactory = (ConnectionFactory) context.lookup(JNDI_CONNECTION_FACTORY);

			Connection connection = connectionFactory.createConnection();

			connection.start();

			Queue queue = (Queue) context.lookup(JNDI_QUEUE);

			Session session = connection.createSession(NON_TRANSACTED, Session.CLIENT_ACKNOWLEDGE);

			return session.createConsumer(queue);
		} catch (Exception jmsException)
		{
			throw new RuntimeException(jmsException);
		}
	}

	public ProducerTransAck createSenderToLive(int MESSAGES_NUM)
	{
//		return new SoakProducerClientAck(
//				getLiveServerID(),
//				SERVER_IP_LIVE,
//				getJNDIPort(),
//				JNDI_QUEUE,
//				MESSAGES_NUM);
		return new ProducerTransAck(
				container(1),
				JNDI_QUEUE,
				MESSAGES_NUM);
	}


	public ControllableProxy createProxyToLive()
	{
		return new SimpleProxyServer(
				SERVER_IP_LIVE,
				MESSAGING_TO_LIVE_REAL_PORT,
				MESSAGING_TO_LIVE_PROXY_PORT);
	}
	public ControllableProxy createProxyToBackup()
	{
		return new SimpleProxyServer(
				SERVER_IP_BACKUP,
				MESSAGING_TO_BACKUP_REAL_PORT,
				MESSAGING_TO_BACKUP_PROXY_PORT);
	}

}