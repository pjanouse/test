package org.jboss.qa.hornetq.test.cli.attributes;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.as.cli.scriptsupport.CLI;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.management.cli.CliConfiguration;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Created by mnovak on 9/16/14.
 * <p>
 * Test remove Jndi operation.
 */
@Category(FunctionalTests.class)
public class CliReloadTest extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(CliReloadTest.class);

    CliConfiguration cliConf = new CliConfiguration(container(1).getHostname(), container(1).getPort(), container(1).getUsername(), container(1).getPassword());

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void reloadServerTest() throws InterruptedException {

        container(1). start();
        for (int i = 0; i < 100;) {

            try {
                CLI cli = CLI.newInstance();
                logger.info("!!!!!!!!!!!!!!!!!!! Connect !!!!!!!!!!!!!!!!!!!!!!");
                cli.connect(cliConf.getHost(), cliConf.getPort(), cliConf.getUser(), cliConf.getPassword());
                logger.info("!!!!!!!!!!!!!!!!!!! Reload !!!!!!!!!!!!!!!!!!!!!!");
                cli.cmd("reload");
                logger.info("!!!!!!!!!!!!!!!!!!! Disconnect !!!!!!!!!!!!!!!!!!!!!!");
                cli.disconnect();
                logger.info("!!!!!!!!!!!!!!!!!!! Done !!!!!!!!!!!!!!!!!!!!!!");

                Assert.assertTrue(CheckServerAvailableUtils.waitForLiveServerToReload(cliConf.getHost(), cliConf.getPort(), 60000));
                i++;
            } catch (IllegalStateException e) {
                logger.warn(e);
            }
        }
        container(1).stop();
    }
}

