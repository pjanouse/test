package org.jboss.qa.hornetq.tools;

import java.util.ArrayList;
import java.util.List;

public class CpuLoadGenerator implements Runnable {

    private static int numberOfThreads = 200;

    private long loadDuration = 900000; //15min;

    public static void main(String[] args) throws Exception {
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

    public void setLoadDuration(long loadDuration) {
        this.loadDuration = loadDuration;
    }

    public void run() {
        new PiCalc().calculate(loadDuration);
    }

}
