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

    /**
     * @see {@link ServerKillProcessor#kill(org.jboss.arquillian.container.spi.Container)}
     */
    @Override
    public void kill(Container container) throws Exception {
        final String KILL_SEQUENCE = "[jbossHome]/bin/jboss-cli.[suffix] --controller=[hostname]:9999 --connect quit";
        final int MAXIMAL_CHECKS = 120;

        log.info("Waiting. Server will be killed by an external process ...");

        String hostname = container.getContainerConfiguration().getContainerProperties().get("managementAddress");
        if (hostname == null) {
            hostname = "127.0.0.1";
        }
        String jbossHome = container.getContainerConfiguration().getContainerProperties().get("jbossHome");
        if (jbossHome == null) {
            jbossHome = System.getenv().get("JBOSS_HOME");
            if (jbossHome == null) {
                throw new RuntimeException("Cannot get JBoss home folder");
            }
        }
        String os = System.getProperty("os.name").toLowerCase();
        String suffix = (os.contains("windows")) ? "bat" : "sh";

        // Prepare kill sequence
        String killSequence = KILL_SEQUENCE.replace("[hostname]", hostname);
        killSequence = killSequence.replace("[jbossHome]", jbossHome);
        killSequence = killSequence.replace("[suffix]", suffix);
        log.info(String.format("Kill sequence for server: '%s'", killSequence));

        int checkCount = 0;
        boolean killed = false;
        do {
            if (checkJBossAlive(killSequence)) {
                Thread.sleep(250);
                log.info(String.format("JBossAS is still alive ... check %s/%s", checkCount, MAXIMAL_CHECKS));
            } else {
                killed = true;
                log.info("JBossAS was killed ...");
                break;
            }
        } while (checkCount++ < MAXIMAL_CHECKS);

        if (killed) {
            log.info("jboss-as killed by byteman script");
        } else {
            throw new RuntimeException("jboss-as not killed");
        }
    }

    /**
     * Checks if AS is alive
     *
     * @param killSequence External command used for checking if server is alive or not
     * @return true if as is alive
     * @throws Exception if something goes wrong
     */
    private boolean checkJBossAlive(String killSequence) throws Exception {
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