package org.jboss.arquillian.extension;


import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Logger;
import org.jboss.arquillian.container.spi.Container;
import org.jboss.arquillian.container.spi.ServerKillProcessor;

public class JBossAS7ServerKillProcessor implements ServerKillProcessor {
	private static final Logger log = Logger.getLogger(
			JBossAS7ServerKillProcessor.class.getName());
	private static String killSequence = "[jbossHome]/bin/jboss-cli.[suffix] --connect quit";

    @Override
	public void kill(Container container) throws Exception {
		log.info("waiting for byteman to kill server");
                
		String jbossHome = System.getenv().get("JBOSS_HOME");
		if(jbossHome == null) {
			jbossHome = container.getContainerConfiguration().getContainerProperties().get("jbossHome");
		}
		killSequence = killSequence.replace("[jbossHome]", jbossHome);

		String suffix;
		String os = System.getProperty("os.name").toLowerCase();
		if(os.contains("windows")) {
			suffix = "bat";
		} else {
			suffix = "sh";
		}
		killSequence = killSequence.replace("[suffix]", suffix);
		log.info("killsequence: " + killSequence);
		int checkn = 0;
		boolean killed = false;
        int numOfCheck = 120;
        do {
			if(checkJBossAlive()) {
                int checkDurableTime = 10;
                Thread.sleep(checkDurableTime * 1000);
				log.info("jboss-as is alive");
			} else {
				killed = true;
                                log.info("jboss-as is killed");
				break;
                                
			}
			checkn ++;
		} while(checkn < numOfCheck);
		
		if(killed) {
			log.info("jboss-as killed by byteman scirpt");
		} else {
			throw new RuntimeException("jboss-as not killed");
		}
	}
	
	private boolean checkJBossAlive() throws Exception {
		Process p = Runtime.getRuntime().exec(killSequence);
                
		p.waitFor();

		InputStream out = p.getInputStream();
		BufferedReader in = new BufferedReader(new InputStreamReader(out));
		String result= in.readLine();
                
                log.info(result);
                
                if (p.exitValue() != 0) {
			throw new RuntimeException("Kill Sequence failed");
		}
                
		return !(result != null && result.contains("The controller is not available"));
	}
}