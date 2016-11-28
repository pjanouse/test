package org.jboss.qa.hornetq.test.journalreplication.utils;

import java.rmi.RemoteException;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.tools.ControllableProxy;

/**
 * @author <a href="dpogrebn@redhat.com">Dmytro Pogrebniuk</a>
 *
 */
public class NetworkProblemController extends Thread
{
	private static final Logger log = Logger.getLogger(NetworkProblemController.class);
	
	private ControllableProxy proxy;
	private int initialDelaySeconds;

	public NetworkProblemController(ControllableProxy proxy, int initialDelaySeconds)
	{
		this.proxy = proxy;
		this.initialDelaySeconds = initialDelaySeconds;
	}

	@Override
	public void run()
	{
		log.info("initial network problem delay: " + initialDelaySeconds + "s");
		sleepSeconds(initialDelaySeconds);

		for (int i = 1; i < 10; i++)
		{
			blockCommunication(true);

			log.info("Proxy is stopped: 1s");

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
	
	private void sleepSeconds(int seconds)
	{
		ThreadUtil.sleepSeconds(seconds);
	}
}
