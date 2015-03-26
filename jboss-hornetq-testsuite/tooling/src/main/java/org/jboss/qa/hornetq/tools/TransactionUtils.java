package org.jboss.qa.hornetq.tools;

import org.apache.log4j.Logger;
import org.junit.Assert;

import java.io.File;
import java.util.Scanner;

/**
 * @author mnovak@redhat.com
 */
public class TransactionUtils {

    private static final Logger log = Logger.getLogger(TransactionUtils.class);

    /**
     * Wait for given time-out for no xa transactions in prepared state.
     *
     * @param timeout
     * @param container
     * @throws Exception
     */
    public void waitUntilThereAreNoPreparedHornetQTransactions(long timeout, org.jboss.qa.hornetq.Container container) throws Exception {

        // check that number of prepared transaction gets to 0
        log.info("Get information about transactions from HQ:");

        long startTime = System.currentTimeMillis();

        int numberOfPreparedTransaction = 100;

        JMSOperations jmsOperations = container.getJmsOperations();

        while (numberOfPreparedTransaction > 0 && System.currentTimeMillis() - startTime < timeout) {

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
     * Checks whether file contains given string.
     *
     * @param fileToCheck
     * @return true if file contains the string
     * @throws Exception
     */
    public boolean checkThatFileContainsUnfinishedTransactionsString(File fileToCheck, String stringToFind) throws Exception {

        return checkThatFileContainsGivenString(fileToCheck, stringToFind);

    }

    /**
     * Checks whether file contains given string.
     *
     * @param fileToCheck
     * @return true if file contains the string, false if not
     * @throws Exception if file does not exist or any other error
     */
    public boolean checkThatFileContainsGivenString(File fileToCheck, String stringToFind) throws Exception {
        Scanner scanner = new Scanner(fileToCheck);

        //now read the file line by line...
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.contains(stringToFind)) {
                return true;
            }
        }
        return false;
    }


}
