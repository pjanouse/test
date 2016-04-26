package org.jboss.qa.hornetq.tools.measuring;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import java.util.Arrays;
import java.util.List;

/**
 * Created by mstyk on 4/12/16.
 */
public class ThreadMeasurement implements Measurable {

    private MBeanServerConnection connection;

    private int threadCount = 0;
    private int peakThreadCount = 0;

    public ThreadMeasurement(MBeanServerConnection connection) throws Exception {
        if (connection == null) {
            throw new IllegalArgumentException("mBeanServerConnection provived to class ThreadMeasurement was null");
        }
        this.connection = connection;
        resetPeakCounter();
    }

    @Override
    public List<String> measure() {
        threadCount = getThreadCount();
        peakThreadCount = getPeakThreadCount();
        try {
            resetPeakCounter();
        } catch (Exception e) {
            Measure.LOGGER.warn("Error reseting peak counter");
        }
        return Arrays.asList(String.valueOf(threadCount), String.valueOf(peakThreadCount));
    }

    @Override
    public List<String> getHeaders() {
        return Arrays.asList("Threads", "Threads peak");
    }

    private int getThreadCount() {
        return getThreads("ThreadCount");
    }

    private int getPeakThreadCount() {
        return getThreads("PeakThreadCount");
    }

    private int getThreads(String attribute) {
        int threadCount = 0;
        try {
            threadCount = (Integer) connection.getAttribute(new ObjectName("java.lang:type=Threading"), attribute);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return threadCount;
    }

    private void resetPeakCounter() throws Exception {
        connection.invoke(new ObjectName("java.lang:type=Threading"), "resetPeakThreadCount", null, null);
    }

}
