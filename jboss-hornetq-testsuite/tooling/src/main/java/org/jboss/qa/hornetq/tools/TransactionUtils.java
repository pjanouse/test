package org.jboss.qa.hornetq.tools;

import org.apache.log4j.Logger;
import org.junit.Assert;

import java.io.File;

/**
 * @author mnovak@redhat.com
 */
public class TransactionUtils {

    private static final Logger log = Logger.getLogger(CheckFileContentUtils.class);

    /**
     * Wait for given time-out for no xa transactions in prepared state.
     *
     * @param timeout
     * @param container
     * @param toleratedNumberOfTransactions
     * @throws Exception
     */
    public void waitUntilThereAreNoPreparedHornetQTransactions(long timeout, org.jboss.qa.hornetq.Container container, int toleratedNumberOfTransactions) throws Exception {

        // check that number of prepared transaction gets to 0
        log.info("Get information about transactions from HQ:");

        long startTime = System.currentTimeMillis();

        int numberOfPreparedTransaction = 100;

        JMSOperations jmsOperations = container.getJmsOperations();

        while (numberOfPreparedTransaction > toleratedNumberOfTransactions && System.currentTimeMillis() - startTime < timeout) {

            numberOfPreparedTransaction = jmsOperations.getNumberOfPreparedTransaction();

            Thread.sleep(1000);

        }

        jmsOperations.close();

        if (System.currentTimeMillis() - startTime > timeout) {
            log.error("There are prepared transactions in HornetQ journal.");
            Assert.fail("There are prepared transactions in HornetQ journal - number of prepared transactions is: " + numberOfPreparedTransaction);
        }
    }

    /**
     * Wait for given time-out for no xa transactions in prepared state.
     *
     * @param timeout
     * @param container
     * @throws Exception
     */
    public void waitUntilThereAreNoPreparedHornetQTransactions(long timeout, org.jboss.qa.hornetq.Container container) throws Exception {
        waitUntilThereAreNoPreparedHornetQTransactions(timeout, container, 0);
    }

    /**
     * Checks whether file contains given string.
     *
     * @param fileToCheck
     * @return true if file contains the string
     * @throws Exception
     */
    public boolean checkThatFileContainsUnfinishedTransactionsString(File fileToCheck, String stringToFind) throws Exception {

        return CheckFileContentUtils.checkThatFileContainsGivenString(fileToCheck, stringToFind);

    }


}
