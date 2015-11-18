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
    public boolean waitUntilThereAreNoPreparedHornetQTransactions(long timeout, org.jboss.qa.hornetq.Container container, int toleratedNumberOfTransactions, boolean failTestIfUnfinishedTransactions) throws Exception {

        // check that number of prepared transaction gets to 0
        log.info("Get information about transactions from HornetQ/Artemis:");

        long startTime = System.currentTimeMillis();

        int numberOfPreparedTransaction = 100;

        JMSOperations jmsOperations = container.getJmsOperations();

        while (numberOfPreparedTransaction > toleratedNumberOfTransactions && System.currentTimeMillis() - startTime < timeout) {

            numberOfPreparedTransaction = jmsOperations.getNumberOfPreparedTransaction();

            Thread.sleep(1000);

        }

        jmsOperations.close();

        if (System.currentTimeMillis() - startTime > timeout) {
            log.error("There are prepared transactions in HornetQ/Artemis journal on node: " + container.getName() + " after timeout: " + timeout);
            if (failTestIfUnfinishedTransactions) {
                Assert.fail("There are prepared transactions in HornetQ/Artemis journal - number of prepared transactions is: " + numberOfPreparedTransaction);
            } else {
                log.warn("There are prepared transactions in HornetQ/Artemis journal - number of prepared transactions is: " + numberOfPreparedTransaction);
                return false;
            }
        }
        return true;
    }

    /**
     * Wait for given time-out for no xa transactions in prepared state.
     *
     * @param timeout
     * @param container
     * @throws Exception
     */
    public void waitUntilThereAreNoPreparedHornetQTransactions(long timeout, org.jboss.qa.hornetq.Container container) throws Exception {
        waitUntilThereAreNoPreparedHornetQTransactions(timeout, container, 0, true);
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
