package org.jboss.qa.hornetq.test.cli.attributes;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.as.cli.scriptsupport.CLI;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.CheckFileContentUtils;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.management.cli.CliConfiguration;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.File;

/**
 * @author mnovak@redhat.com
 * @tpChapter Integration testing
 * @tpSubChapter Administration of HornetQ component
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-ipv6-tests/
 * @tpTcmsLink https://polarion.engineering.redhat.com/polarion/#/project/EAP7/wiki/JMS/EAP%207_x%20ActiveMQ%20Artemis%20Test%20Plan_%20Technical%20Details
 * @tpTestCaseDetails Test case checks that reloading of EAP 7 server is ok.
 */
@Category(FunctionalTests.class)
public class CliReloadTest extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(CliReloadTest.class);

    CliConfiguration cliConf = new CliConfiguration(container(1).getHostname(), container(1).getPort(), container(1).getUsername(), container(1).getPassword());

    /**
     * @tpTestDetails Start server in standalone-full-ha.xml profile and reload 100 times. Always wait for EAP server to get
     * to running state before reloading server again.
     * @tpPassCrit Check there are not exception from :reload operation calls.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void reloadServerTestWaitForFullReload() throws Exception {
        reloadServerTest(100, true);
    }

    /**
     * @tpTestDetails Start server in standalone-full-ha.xml profile and reload 100 times. Do not wait for EAP server to get
     * to running state before reloading server again.
     * @tpPassCrit Check there are not exception from :reload operation calls.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void reloadServerTestDoNotWaitForFullReload() throws Exception {
        reloadServerTest(100, false);
    }

    public void reloadServerTest(int numberOfReloads, boolean waitForServerToReload) throws Exception {

        container(1).start();
        for (int i = 0; i < numberOfReloads; i++) {

            try {
                CLI cli = CLI.newInstance();
                logger.info("!!!!!!!!!!!!!!!!!!! Connect !!!!!!!!!!!!!!!!!!!!!!");
                cli.connect(cliConf.getHost(), cliConf.getPort(), cliConf.getUser(), cliConf.getPassword());
                logger.info("!!!!!!!!!!!!!!!!!!! Reload !!!!!!!!!!!!!!!!!!!!!!");
                cli.cmd("reload");
                logger.info("!!!!!!!!!!!!!!!!!!! Disconnect !!!!!!!!!!!!!!!!!!!!!!");
                cli.disconnect();
                logger.info("!!!!!!!!!!!!!!!!!!! Done !!!!!!!!!!!!!!!!!!!!!!");

                if (waitForServerToReload) {
                    Assert.assertTrue(CheckServerAvailableUtils.waitForLiveServerToReload(cliConf.getHost(), cliConf.getPort(), 60000));
                } else {
                    Thread.sleep(4000);
                    Assert.assertTrue(CheckServerAvailableUtils.waitHornetQToAlive(container(1), 60000));
                }

            } catch (IllegalStateException e) {
                logger.warn(e);
            }
        }

        container(1).stop();

        // check that logs does not contains Exceptions
        Assert.assertFalse("Server " + container(1).getName() + " cannot contain exceptions but there are. " +
                "Check logs of the server for details. Server logs for failed tests are archived in target directory " +
                "of the maven module with this test.", checkServerLog(container(1)));
    }

    private boolean checkServerLog(Container container) throws Exception {
        StringBuilder pathToServerLog = new StringBuilder(container.getServerHome());
        pathToServerLog.append(File.separator).append("standalone").append(File.separator)
                .append("log").append(File.separator).append("server.log");
        return CheckFileContentUtils.checkThatFileContainsGivenString(new File(pathToServerLog.toString()), "Exception");
    }
}

