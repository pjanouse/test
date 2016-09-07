/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.qa.artemis.test.journalexport;

import java.io.File;
import org.apache.log4j.Logger;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.CheckFileContentUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

/**
 *
 * @author mstyk
 */
@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class AIOJournalLoadTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(AIOJournalLoadTestCase.class);

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.JOURNAL_TYPE, value = "ASYNCIO")
    })
    public void testUseOfAIOJournal() throws Exception {
        
        log.info(System.getProperty("os.name"));
        
        Assume.assumeTrue("This test run only on Linux machines", System.getProperty("os.name").contains("Linux"));
        
        Container container = container(1);
        
        container.start();

        StringBuilder pathToServerLogFile = new StringBuilder(container.getServerHome());

        pathToServerLogFile.append(File.separator).append("standalone").append(File.separator).append("log").append(File.separator).append("server.log");

        log.info("Check server.log: " + pathToServerLogFile);

        File serverLog = new File(pathToServerLogFile.toString());

        String stringToFind = "Using AIO Journal";

        Assert.assertTrue("Server is not using AIO journal although it was configured to." , CheckFileContentUtils.checkThatFileContainsGivenString(serverLog, stringToFind));
        
        container.stop();
    }

}
