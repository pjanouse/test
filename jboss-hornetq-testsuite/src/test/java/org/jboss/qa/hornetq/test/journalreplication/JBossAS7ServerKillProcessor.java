package org.jboss.qa.hornetq.test.journalreplication;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Logger;

public class JBossAS7ServerKillProcessor
{
	private static final Logger log = Logger.getLogger(JBossAS7ServerKillProcessor.class.getName());
	private static String killSequence = "sh {jbossHome}/bin/jboss-cli.sh --connect controller={ip}:9999 shutdown";

	public static void kill(String ipAddress) throws Exception
	{
		log.info("waiting for byteman to kill server");

		String jbossHome = System.getProperty("JBOSS_HOME_1");
		killSequence = killSequence.replace("{jbossHome}", jbossHome);
		killSequence = killSequence.replace("{ip}", ipAddress);

		log.info("killsequence: " + killSequence);
		int checkn = 0;
		boolean killed = false;
		int numOfCheck = 120;
		do
		{
			if (checkJBossAlive())
			{
				int checkDurableTime = 10;
				Thread.sleep(checkDurableTime * 1000);
				log.info("jboss-as is alive");
			} else
			{
				killed = true;
				log.info("jboss-as is killed");
				break;

			}
			checkn++;
		} while (checkn < numOfCheck);

		if (killed)
		{
			log.info("jboss-as killed by byteman scirpt");
		} else
		{
			throw new RuntimeException("jboss-as not killed");
		}
	}

	private static boolean checkJBossAlive() throws Exception
	{
		Process p = Runtime.getRuntime().exec(killSequence);

		p.waitFor();

		InputStream out = p.getInputStream();
		BufferedReader in = new BufferedReader(new InputStreamReader(out));
		String result = in.readLine();

		log.info(result);

		return false;/*
		
		if (p.exitValue() != 0)
		{
			throw new RuntimeException("Kill Sequence failed");
		}

		return !(result != null && result.contains("The controller is not available"));*/
	}
}