package org.jboss.qa.hornetq.tools;

import org.apache.log4j.Logger;

import java.util.StringTokenizer;

/**
 * Created by mnovak on 11/11/15.
 */
public class BindProcessToCpuUtils {

    private static final Logger logger = Logger.getLogger(BindProcessToCpuUtils.class);

    public static Process bindProcessToCPU(String pid, String cpuIds) throws Exception {
        String cmd = null;
        if (System.getProperty("os.name").contains("Linux") && (System.getProperty("os.version").contains("el7") || System.getProperty("os.version").contains("fc2"))) {
            cmd = "taskset -a -cp " + cpuIds + " " + pid;
        } else if (System.getProperty("os.name").contains("Windows") || System.getProperty("os.name").contains("windows")) {
            String cpuMask = convertToDecimal(cpuIds);
            cmd = "PowerShell \"GET-PROCESS -id " + pid + " | $RESULTS.ProcessorAffinity=" + cpuMask + "\"";
        } else {
            throw new RuntimeException("Command for binding process to CPU core is not implemented for this OS. Check BindProcessToCpuUtils.bindProcessToCPU() and implement for your OS.");
        }

        logger.info("Command: " + cmd);
        return Runtime.getRuntime().exec(cmd);
    }

    private static String convertToDecimal(String cpuIds) throws Exception{

        double mask = 0;
        // for all cpuIds
        StringTokenizer tokens = new StringTokenizer(cpuIds, ",");
        while (tokens.hasMoreTokens()){
            mask = mask + Math.pow(2, Double.valueOf(tokens.nextToken()));
        }

        return String.valueOf(new Double(mask).intValue());
    }

    // just for testing
    public static void main(String[] args) throws Exception {
//        bindProcessToCPU("3227", "0");
//        System.out.println("Mask is: " + convertToDecimal("0,1"));
//        System.getProperties().list(System.out);
    }

}
