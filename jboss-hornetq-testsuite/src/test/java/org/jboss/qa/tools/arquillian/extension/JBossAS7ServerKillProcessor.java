package org.jboss.qa.tools.arquillian.extension;


import org.jboss.arquillian.container.spi.Container;
import org.jboss.arquillian.container.spi.ServerKillProcessor;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Logger;

/**
 * Implementation of the @see {@link ServerKillProcessor} for AS7 and HornetQ tests
 */
public class JBossAS7ServerKillProcessor implements ServerKillProcessor {

    // Logger
    private static final Logger log = Logger.getLogger(JBossAS7ServerKillProcessor.class.getName());

    // Kill sequence for CLI
    private static String killSequence = "[jbossHome]/bin/jboss-cli.[suffix] --connect quit";

    /**
     * @see {@link ServerKillProcessor#kill(org.jboss.arquillian.container.spi.Container)}
     */
    @Override
    public void kill(Container container) throws Exception {
        final int MAXIMAL_CHECKS = 120;
        log.info("Waiting for Byteman to kill server");

        String jbossHome = System.getenv().get("JBOSS_HOME");
        if (jbossHome == null) {
            jbossHome = container.getContainerConfiguration().getContainerProperties().get("jbossHome");
        }
        killSequence = killSequence.replace("[jbossHome]", jbossHome);

        String suffix;
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("windows")) {
            suffix = "bat";
        } else {
            suffix = "sh";
        }
        killSequence = killSequence.replace("[suffix]", suffix);
        log.info("Kill sequence for server: " + killSequence);
        int checkCount = 0;
        boolean killed = false;
        do {
            if (checkJBossAlive()) {
                int checkDurableTime = 10;
                Thread.sleep(checkDurableTime * 50);
                log.info("JBossAS is still alive ...");
            } else {
                killed = true;
                log.info("JBossAS is dead ...");
                break;
            }
        } while (checkCount++ < MAXIMAL_CHECKS);

        if (killed) {
            log.info("jboss-as killed by byteman scirpt");
        } else {
            throw new RuntimeException("jboss-as not killed");
        }
    }

    /**
     * Checks if AS is alive
     *
     * @return true if as is alive
     * @throws Exception if something goes wrong
     */
    private boolean checkJBossAlive() throws Exception {
        Process p = Runtime.getRuntime().exec(killSequence);
        p.waitFor();
        InputStream out = p.getInputStream();
        BufferedReader in = new BufferedReader(new InputStreamReader(out));
        String result = in.readLine();
        log.info(result);
        if (p.exitValue() != 0) {
            throw new RuntimeException("Kill Sequence failed");
        }
        return !(result != null && result.contains("The controller is not available"));
    }
}