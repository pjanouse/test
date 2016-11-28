package org.jboss.qa.hornetq.tools.arquillian.extension;

import org.jboss.arquillian.container.spi.Container;
import org.jboss.arquillian.container.spi.ServerKillProcessor;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.JMSTools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.*;


/**
 * Implementation of the @see {@link ServerKillProcessor} for AS7 and HornetQ tests
 */
public class JBossAS7ServerKillProcessor implements ServerKillProcessor {

    // Logger
    private static final Logger log = Logger.getLogger(JBossAS7ServerKillProcessor.class);

    String hostname = null;
    int managementPort = 0;

    /**
     * @see {@link ServerKillProcessor#kill(org.jboss.arquillian.container.spi.Container)}
     */
    @Override
    public void kill(Container container) throws Exception {

        // try to get property specific for EAP 6 and if succeed use EAP 6 dead detection mechanism
        String serverConfig = container.getContainerConfiguration().getContainerProperties().get("serverConfig");
        // if EAP 6
        if (serverConfig != null) {
            killEAP6(container);
        } else {
            killEAP5(container);
        }
    }

    private void killEAP5(Container container) throws Exception {

        int checkCount = 0;
        boolean killed = false;

        String hostname = container.getContainerConfiguration().getContainerProperties().get("bindAddress");
        int port = Integer.valueOf(container.getContainerConfiguration().getContainerProperties().get("rmiPort"));
        final int MAXIMAL_CHECKS = 120;

        do {
            if (pingEAP5(hostname, port)) {
                int checkDurableTime = 10;
                Thread.sleep(checkDurableTime * 50);
                log.info("JBossAS is still alive ..." + hostname);
            } else {
                killed = true;
                log.info("JBossAS is dead ..." + hostname);
                break;
            }
        } while (checkCount++ < MAXIMAL_CHECKS);

        if (killed) {
            log.info("jboss-as killed by byteman script");
        } else {
            throw new RuntimeException("jboss-as not killed");
        }
    }

    private boolean pingEAP5(String hostname, int port) {

        int code = 0;

        HttpURLConnection connection = null;

        try {

            URL u = new URL("http://" + hostname + ":" + port);
            connection = (HttpURLConnection) u.openConnection();
            connection.setRequestMethod("HEAD");
            code = connection.getResponseCode();

            // You can determine on HTTP return code received. 200 is success.
        } catch (ConnectException e) {
            // ignore
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return code == 200;
    }

    private void killEAP6(Container container) throws Exception {

        hostname = container.getContainerConfiguration().getContainerProperties().get("managementAddress");
        if (hostname == null) {
            hostname = "127.0.0.1";
        }

        managementPort = Integer.valueOf(container.getContainerConfiguration().getContainerProperties().get("managementPort"));

//        String port = container.getContainerConfiguration().getContainerProperties().get("managementPort");
//        if (port == null) {
//            port = "9999";
//        }
//
//        final String KILL_SEQUENCE = "[jbossHome]/bin/jboss-cli.[suffix] --controller=[hostname]:[port] --password=minono532/20 --user=admin --connect quit";
        final int MAXIMAL_CHECKS = 120;

        log.info("Waiting. Server will be killed by an external process ...");

//        String jbossHome = container.getContainerConfiguration().getContainerProperties().get("jbossHome");
//        if (jbossHome == null) {
//            jbossHome = System.getenv().get("JBOSS_HOME");
//            if (jbossHome == null) {
//                throw new RuntimeException("Cannot get JBoss home folder");
//            }
//        }
//        String os = System.getProperty("os.name").toLowerCase();
//        String suffix = (os.contains("windows")) ? "bat" : "sh";
//
//        String killSequence;
//        // Prepare kill sequence
//        if (JMSTools.isIpv6Address(hostname))   {
//            killSequence = KILL_SEQUENCE.replace("[hostname]", "[" + hostname + "]");
//        } else {
//            killSequence = KILL_SEQUENCE.replace("[hostname]", hostname);
//        }
//        killSequence = killSequence.replace("[port]", port);
//        killSequence = killSequence.replace("[jbossHome]", jbossHome);
//        killSequence = killSequence.replace("[suffix]", suffix);
//        log.info(String.format("Kill sequence for server: '%s'", killSequence));

        if (JMSTools.isIpv6Address(hostname))   {
            hostname = hostname.replace(hostname, "[" + hostname + "]");
        }
        int checkCount = 0;
        boolean killed = false;
        do {
            if (checkJBossAlive()) {
                int checkDurableTime = 10;
                Thread.sleep(checkDurableTime * 50);
                log.info("JBossAS is still alive ..." + hostname);
            } else {
                killed = true;
                log.info("JBossAS is dead ..." + hostname);
                break;
            }
        } while (checkCount++ < MAXIMAL_CHECKS);

        if (killed) {
            log.info("jboss-as killed by byteman script");
        } else {
            throw new RuntimeException("jboss-as not killed");
        }
    }

//    /**
//     * Checks if AS is alive
//     *
//     * @return true if as is alive
//     * @throws Exception if something goes wrong
//     */
//    private boolean checkJBossAlive() throws Exception {
//
//        final Process p;
////        String os = System.getProperty("os.name").toLowerCase();
////
////        if (os.contains("windows")) {
//
//        String response;
//        String url = "http://" + hostname + ":" + managementPort;
//        log.info("Calling url: " + url + " to check that server is dead.");
//        try {
//            response = HttpRequest.get(url, 20, TimeUnit.SECONDS);
//        } catch (Exception ex) {
//            ex.printStackTrace();
//            return false;
//        }
//
//        return !(response == null || "".equals(response) || response.contains("Unable to connect"));
//
////        } else {
////            boolean stillRunning = true;
////
////            p = Runtime.getRuntime().exec(killSequence);
////
////            p.waitFor();
////
////            // check standard output - false returned then server is stopped
////            if (!checkOutput(p.getInputStream())) {
////                stillRunning = false;
////            }
////
////            // check error output - false returned then server is stopped
////            if (!checkOutput(p.getErrorStream())) {
////                stillRunning = false;
////            }
////
////            if (p.exitValue() != 0) {
////                log.error("Return code from kill sequence is different from zero. It's expected when server is no longer"
////                        + " started but it can also mean that kill sequence does not work. Kill sequence: " + killSequence);
////            }
////            return stillRunning;
////        }
//
//
//    }

    /**
     * Returns true if something is listening on server
     *
     */
    protected boolean checkJBossAlive() {
        log.debug("Check that port is open - IP address: " + hostname + " port: " + managementPort);
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(hostname, managementPort), 100);
            return true;
        } catch (Exception ex) {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return false;
        }
    }

    /**
     * Verify output from the stream.
     *
     * @param in Standard/error output from the kill sequence - expected string
     * @return true - when server lives, false - when server was killed or kill sequence failed
     */
    private boolean checkOutput(InputStream in) throws IOException {

        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        String result;
        while ((result = br.readLine()) != null) {
            log.info(result);
            // EAP 6
            if (result.contains("The controller is not available")) {
                return false;
            }
            // EAP 5
            if (result.contains("ERROR [Twiddle] Exec failed")) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws Exception {
        JBossAS7ServerKillProcessor p = new JBossAS7ServerKillProcessor();
        p.hostname = "127.0.0.1";
        p.managementPort = 9999;
        while (p.checkJBossAlive()) {
            System.out.println("mnovak: jboss is alive");
            Thread.sleep(200);
        }
//        System.out.println("mnovak: jboss is dead");
    }
}