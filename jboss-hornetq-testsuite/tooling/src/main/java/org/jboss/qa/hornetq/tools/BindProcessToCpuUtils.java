package org.jboss.qa.hornetq.tools;

import org.apache.log4j.Logger;

/**
 * Created by mnovak on 11/11/15.
 */
public class BindProcessToCpuUtils {

    private static final Logger logger = Logger.getLogger(BindProcessToCpuUtils.class);

    public static Process bindProcessToCPU(String pid, String cpuIds) throws Exception {
        if (!System.getProperty("os.name").contains("Linux")) {
            throw new UnsupportedOperationException("Operation bind process to cpu is supported only on linux. " +
                    "Curren operation system is: " + System.getProperty("os.name").contains("Linux"));
        }
        String cmd = "taskset -a -cp " + cpuIds + " " + pid;
        logger.info("Command: " + cmd);
        return Runtime.getRuntime().exec(cmd);
    }

    // just for testing
    public static void main(String[] args) throws Exception {
        bindProcessToCPU("3227", "0");
    }

}
