package org.jboss.qa.hornetq.tools;

import org.jboss.logging.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
     * @param stringToFind
     * @return true if file contains the string, false if not
     * @throws Exception if file does not exist or any other error
     */
    public static boolean checkThatFileContainsGivenString(File fileToCheck, String stringToFind) throws Exception {
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

    /**
     * Checks whether file contains given regex.
     *
     * @param fileToCheck
     * @param regex
     * @return true if file contains the string, false if not
     * @throws Exception if file does not exist or any other error
     */
    public static boolean checkThatFileContainsGivenRegex(File fileToCheck, String regex) throws Exception {
        Scanner scanner = new Scanner(fileToCheck);

        //now read the file line by line...
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.matches(regex)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether file contains given regex.
     *
     * @param fileToCheck
     * @param regex
     * @return list of lines with given regex
     * @throws Exception if file does not exist or any other error
     */
    public static List<String> findRegexInFile(File fileToCheck, String regex) throws Exception {
        Scanner scanner = new Scanner(fileToCheck);

        //now read the file line by line...
        List<String> result = new ArrayList<String>();
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.matches(regex)) {
                result.add(line);
            }
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        String regex = "(.*)(WARN|ERROR)(.*)(org.apache.activemq.artemis|io.netty)(.*)";

        StringBuilder pathToServerLog = new StringBuilder("/home/mnovak/hornetq_eap6_dev/internal/eap-tests-hornetq/scripts/server2/jboss-eap");
        pathToServerLog.append(File.separator).append("standalone").append(File.separator)
                .append("log").append(File.separator).append("server.log");

        System.out.println(findRegexInFile(new File(pathToServerLog.toString()), regex).toString());

    }
}
