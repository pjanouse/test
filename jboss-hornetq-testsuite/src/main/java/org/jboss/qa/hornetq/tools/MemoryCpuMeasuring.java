package org.jboss.qa.hornetq.tools;

import org.apache.log4j.Logger;

import java.io.IOException;

/**
 * Start script create_memory_cpu_csv.sh in resources directory.
 * <p/>
 * Created by mnovak on 11/5/14.
 */
public class MemoryCpuMeasuring {

    private static final Logger log = Logger.getLogger(MemoryCpuMeasuring.class);

    Process p = null;

    public void startMeasuring(long pid, String prefixOfCsvFiles) {
        // start create_memory_cpu_csv.sh
        log.info("Start measuring process: " + pid + " and store info to files with prefix: " + prefixOfCsvFiles);
        ProcessBuilder processBuilder = new ProcessBuilder("src/test/resources/create_memory_cpu_csv.sh", String.valueOf(pid), prefixOfCsvFiles);
        try {
            p = processBuilder.start();
        } catch (IOException e) {
            log.error(e);
            if (p != null) {
                p.destroy();
            }
        }
    }

    public void stopMeasuring() {
        if (p != null) {
            p.destroy();
        } else {
            log.error("You have to start measuring before stopping it. Process is null.");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        MemoryCpuMeasuring m = new MemoryCpuMeasuring();
        m.startMeasuring(14131, "myserver");
        Thread.sleep(60000);
        m.stopMeasuring();

    }
}
