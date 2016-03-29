package org.jboss.qa.hornetq.tools;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.impl.ArtemisJMSImplementation;
import org.jboss.qa.hornetq.apps.impl.HornetqJMSImplementation;
import org.jboss.qa.hornetq.constants.Constants;

import java.io.*;

/**
 * Created by mnovak on 4/14/15.
 *
 * @author mnovak@redhat.com
 */
public class ContainerUtils {

    private static final Logger log = Logger.getLogger(ContainerUtils.class);

    /**
     * Checks whether server is EAP 6 or not.
     *
     * @param container container
     * @return true is container is EAP6 type
     */
    public static boolean isEAP6(Container container) {
        return container.getContainerType().equals(Constants.CONTAINER_TYPE.EAP6_CONTAINER);
    }

    /**
     * Checks whether server is EAP 7 or not.
     *
     * @param container container
     * @return true is container is EAP7 type
     */
    public static boolean isEAP7(Container container) {
        return container.getContainerType().equals(Constants.CONTAINER_TYPE.EAP7_CONTAINER);

    }

    public static JMSImplementation getJMSImplementation(Container container) {
        if (isEAP7(container)) {
            return ArtemisJMSImplementation.getInstance();
        } else {
            return HornetqJMSImplementation.getInstance();
        }
    }

    public static void printThreadDump(Container container) {
        long pid = ProcessIdUtils.getProcessId(container);
        log.info("Print thread dump for container: " + container.getName() + " which has pid: " + pid);
        File toOutputFileForThreadump = new File(ServerPathUtils.getStandaloneLogDirectory(container), container.getName() + "-thread-dump.txt");
        printThreadDump(pid, toOutputFileForThreadump);
    }

    public static File ifFileExistsAddSuffix(File file) {
        File temporaryTestExistenceFile = file;
        int i = 1;
        while (temporaryTestExistenceFile.exists()) {
            temporaryTestExistenceFile = new File(file.getAbsolutePath() + i);
            i++;
        }
        return temporaryTestExistenceFile;
    }

    public static void printThreadDump(long pid, File toOutputFileForThreadump) {

        toOutputFileForThreadump = ifFileExistsAddSuffix(toOutputFileForThreadump);
        log.info("File exists so appending suffix. Printing thread dump for pid: " + pid + " to file: " + toOutputFileForThreadump.getAbsolutePath());


        try {
            if (System.getProperty("os.name").contains("Windows") || System.getProperty("os.name").contains("windows")) { // use taskkill
                log.warn("We cannot print thread dump on Windows machines. Printing thread dump for process: " + pid + " is canceled.");
            } else if (System.getProperty("java.vm.name").contains("Java HotSpot")) {
                Process printThreadDump = null;
                printThreadDump = Runtime.getRuntime().exec("jstack -l " + pid);

                BufferedReader input = new BufferedReader(new InputStreamReader(printThreadDump.getInputStream()));
                String line = null;
                PrintStream printStream = new PrintStream(toOutputFileForThreadump);
                while ((line = input.readLine()) != null) {
                    printStream.println(line);
                }
                printStream.flush();
                printStream.close();
                input.close();
                if (printThreadDump.waitFor() == 0) {
                    log.info("Print thread dump of process: " + pid + " was successful. Check server log for more details.");
                }
            } else {
                log.warn("We cannot print thread dump on IBM JDK java. Printing thread dump for process: " + pid + " is canceled.");
            }

        } catch (Exception ex) {
            log.warn("Creating thread dump for process  " + pid + " failed.", ex);
        }
    }

    public static boolean isStarted(Container container) {
        return CheckServerAvailableUtils.checkThatServerIsReallyUp(container);
    }

    public static void main(String[] args) {
        long pid = 14086;
        printThreadDump(pid, new File("/home/mnovak/threadump.txt"));
    }
}
