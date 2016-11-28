package org.jboss.qa.hornetq.test.journalreplication.utils;

import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.logging.Logger;

/**
 * @author <a href="dpogrebn@redhat.com">Dmytro Pogrebniuk</a>
 *
 */
public class ServerUtil
{
	private static final Logger log = Logger.getLogger(ServerUtil.class);
	
	public static void killServer(String ipAddress)
	{
		try
		{
			JBossAS7ServerKillProcessor.kill(ipAddress);
		} catch (Exception killingException)
		{
			log.error("Exception while killing server ip:[" + ipAddress + "]", 
					  killingException);
			
			throw new RuntimeException(killingException);
		}
	}

	public static void startServer(ContainerController controller, String server)
	{
		controller.start(server);
	}

	public static void stopServer(ContainerController controller, String server)
	{
		controller.stop(server);
	}
}
