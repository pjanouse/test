package org.jboss.qa.hornetq.tools;

import org.apache.log4j.Logger;

import java.io.File;
import java.util.Scanner;

/**
 * @author mnovak@redhat.com
 */
public class CheckFileContentUtils {

    private static final Logger log = Logger.getLogger(CheckFileContentUtils.class);

    /**
     * Checks whether file contains given string.
     *
     * @param fileToCheck
     * @return true if file contains the string, false if not
     * @throws Exception if file does not exist or any other error
     */
    public static boolean   checkThatFileContainsGivenString(File fileToCheck, String stringToFind) throws Exception {
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
