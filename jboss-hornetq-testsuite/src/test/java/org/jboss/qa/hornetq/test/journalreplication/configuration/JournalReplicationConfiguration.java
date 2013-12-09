package org.jboss.qa.hornetq.test.journalreplication.configuration;

import java.io.File;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.NamingException;

import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.qa.hornetq.apps.clients.SoakProducerClientAck;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.hornetq.test.journalreplication.JournalReplicationAbstract;
import org.jboss.qa.hornetq.test.journalreplication.utils.FileUtil;
import org.jboss.qa.hornetq.tools.ControllableProxy;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.SimpleProxyServer;

/**
 * @author <a href="dpogrebn@redhat.com">Dmytro Pogrebniuk</a>
 *
 */
public class JournalReplicationConfiguration
{
	private static final String SERVER_DIR_LIVE = HornetQTestCase.JBOSS_HOME_1;
	private static final String SERVER_DIR_BACKUP = HornetQTestCase.JBOSS_HOME_2;

	private static final String SERVER_LIVE = HornetQTestCase.CONTAINER1;
	private static final String SERVER_BACKUP = HornetQTestCase.CONTAINER2;

	private static final String CLUSTER_PASSWORD = "password";
	private static final String NAME_QUEUE = "Queue1";
	
	private static final String SERVER_IP_LIVE = HornetQTestCase.CONTAINER1_IP;

	private static final String JNDI_QUEUE = "queue/InQueue";
	private static final String NAME_CONNECTION_FACTORY = "RemoteConnectionFactory";
	private static final String JNDI_CONNECTION_FACTORY = "jms/" + NAME_CONNECTION_FACTORY;
	
	private static final int MESSAGING_TO_LIVE_REAL_PORT = 5445;
	private static final int MESSAGING_TO_LIVE_PROXY_PORT = 51111;
	
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

	private ContainerController controller;
	
	public JournalReplicationConfiguration(ContainerController controller)
	{
		System.out.println("controller=" + controller);
		this.controller = controller;
	}

	public void prepareLive(JournalReplicationAbstract journalReplicationAbstractTestCase)
	{
		resetConfiguration(SERVER_DIR_LIVE);

		controller.stop(SERVER_LIVE);
		FileUtil.deleteFolder(new File(SERVER_DIR_LIVE + "/standalone/data"));
		
		 File standaloneModified = new File(
		    	"src" + File.separator +
				"test" + File.separator +
				"java" + File.separator + 
				"org" + File.separator + 
				"jboss" + File.separator + 
				"qa" + File.separator + 
				"hornetq" + File.separator +
				"test" + File.separator +
				"journalreplication" + File.separator +
				"configuration" + File.separator +
				"standalone-full-ha.xml");
		File standaloneOriginal = new File(
				SERVER_DIR_LIVE + File.separator + 
				"standalone" + File.separator + 
				"configuration" + File.separator + 
				"standalone-full-ha.xml");
		
		copyFile(standaloneModified, standaloneOriginal);

		controller.start(SERVER_LIVE);

		JMSOperations adminLive = getJMSOperations(SERVER_LIVE);

		adminLive.setJournalType(journalReplicationAbstractTestCase.getJournalType().name());
		
		adminLive.removeAddressSettings("#");
		
		adminLive.addAddressSettings(
				"#", 
				journalReplicationAbstractTestCase.getAddressFullPolicy().name(), 
				10485760, 
				0, 
				10485760, 
				1048570);

		/*
		adminLive.setSecurityEnabled(true);
		adminLive.setCheckForLiveServer(true);
		adminLive.setClusterUserPassword(CLUSTER_PASSWORD);
		adminLive.setSharedStore(false);
		adminLive.setPersistenceEnabled(true);

		adminLive.createQueue(NAME_QUEUE, JNDI_QUEUE);

		adminLive.addSocketBinding("bindname", "234.255.10.1", 55234);

		adminLive.addSocketBinding(
				proxySocketBindingName = "messaging-via-proxy", 
				port = MESSAGING_TO_LIVE_PROXY_PORT);

		adminLive.createRemoteConnector(
				proxyConnectorName = "netty-proxy", 
				socketBinding = proxySocketBindingName,
				params = null);

		adminLive.setHaForConnectionFactory(NAME_CONNECTION_FACTORY, true);
		adminLive.setFailoverOnShutdown(NAME_CONNECTION_FACTORY, true);
		adminLive.setBlockOnAckForConnectionFactory(NAME_CONNECTION_FACTORY, false);
		adminLive.setRetryIntervalForConnectionFactory(NAME_CONNECTION_FACTORY, 1000L);
		adminLive.setReconnectAttemptsForConnectionFactory(NAME_CONNECTION_FACTORY, 3);
		//adminLive.setConnectorOnConnectionFactory(NAME_CONNECTION_FACTORY, proxyConnectorName);
		
		adminLive.removeClusteringGroup("my-cluster");
		adminLive.setClusterConnections(
				clusterName = "my-cluster", 
				address = "jms", 
				discoveryGroup = "dg-group1",
				forwardWhenNoConsumers = false, 
				maxHops = 1, 
				retryInterval = 1000, 
				useDuplicateDetection = true,
				connectorName = proxyConnectorName);

		adminLive.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
		adminLive.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", true);
		adminLive.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", true);
		adminLive.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", true);
		adminLive.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", true);
		adminLive.setPermissionToRoleToSecuritySettings("#", "guest", "manage", true);
		adminLive.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);
*/
		adminLive.close();

		controller.stop(SERVER_LIVE);
		
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

	    copyFile(applicationUsersModified, applicationUsersOriginal);
	    
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

	    copyFile(applicationRolesModified, applicationRolesOriginal);
	}
	
	public void prepareBackup()
	{
		resetConfiguration(SERVER_DIR_BACKUP);
		controller.stop(SERVER_BACKUP);
		FileUtil.deleteFolder(new File(SERVER_DIR_BACKUP + "/standalone/data"));
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

	    copyFile(applicationUsersModified, applicationUsersOriginal);

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

	    copyFile(applicationRolesModified, applicationRolesOriginal);

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

		copyFile(defaultConfiguration, actualConfiguration);
	}

	private void copyFile(File original, File destination)
	{
		FileUtil.copyFile(original, destination);
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
			Context context = getContext(SERVER_IP_LIVE, getJNDIPort());
	
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
	
	public SoakProducerClientAck createSenderToLive(int MESSAGES_NUM)
	{
		return new SoakProducerClientAck(
				getLiveServerID(),
				SERVER_IP_LIVE,
				getJNDIPort(),
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
	
	private JMSOperations getJMSOperations(String container)
	{
		return new HornetQTestCase().getJMSOperations(container);
	}
	
	private int getJNDIPort()
	{
		return new HornetQTestCase().getJNDIPort();
	}
	
	private Context getContext(String hostName, int port)
	{
		try
		{
			return new HornetQTestCase().getContext(hostName, port);
		} catch (NamingException e)
		{
			throw new RuntimeException(e);
		}
	}
}