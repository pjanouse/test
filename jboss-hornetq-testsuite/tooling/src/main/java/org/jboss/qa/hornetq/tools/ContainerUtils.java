package org.jboss.qa.hornetq.tools;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.impl.ArtemisJMSImplementation;
import org.jboss.qa.hornetq.apps.impl.HornetqJMSImplementation;
import org.jboss.qa.hornetq.constants.Constants;

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
        printThreadDump(pid);
    }

    public static void printThreadDump(long pid) {
        Process printThreadDump = null;
        try {
            if (System.getProperty("os.name").contains("Windows") || System.getProperty("os.name").contains("windows")) { // use taskkill
                log.warn("We cannot print thread dump on Windows machines. Printing thread dump for process: " + pid + " is canceled.");
            } else { // on all other platforms use kill -9
                printThreadDump = Runtime.getRuntime().exec("kill -3 " + pid);
            }
            if (printThreadDump.waitFor() == 0) {
                log.info("Print thread dump of process: " + pid + " was successful. Check server log for more details.");
            }
        } catch (Exception ex) {
            log.warn("Calling kill -3 for process  " + pid + " failed. There is probably no such process.", ex);
        }
    }
}
