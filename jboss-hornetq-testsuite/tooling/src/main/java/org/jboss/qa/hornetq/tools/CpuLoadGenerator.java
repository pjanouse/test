package org.jboss.qa.hornetq.tools;

import java.util.ArrayList;
import java.util.List;

/**
 * Takes one argument - load duration. Specifies for how long to generate load. Default 15 min.
 */
public class CpuLoadGenerator implements Runnable {

    private static int numberOfThreads = 200;

    private static long loadDuration = 900000; //15min;

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0] != null && !"".equals(args[0]))    {
            loadDuration = Long.valueOf(args[0]);
        }
        generateLoad();
    }

    public static void generateLoad() throws Exception {
        List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i < numberOfThreads; i++) {
            Thread t = new Thread(new CpuLoadGenerator());
            threads.add(t);
            t.start();
            System.out.println("Starting thread: " + t.getName());
        }

        for (Thread t : threads) {
            t.join();
            System.out.println("Thread " + t.getName() + " finished.");
        }
    }

    public void run() {
        new PiCalc().calculate(loadDuration);
    }

}
