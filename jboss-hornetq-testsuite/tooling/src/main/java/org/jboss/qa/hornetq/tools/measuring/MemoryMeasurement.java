package org.jboss.qa.hornetq.tools.measuring;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeDataSupport;
import java.util.Arrays;
import java.util.List;

/**
 * Created by mstyk on 4/12/16.
 */
public class MemoryMeasurement implements Measurable {

    private MBeanServerConnection connection;

    private Long heapSize = 0L;
    private Long nonHeapSize = 0L;

    public MemoryMeasurement(MBeanServerConnection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("mBeanServerConnection provived to class MemoryMeasurement was null");
        }
        this.connection = connection;
    }

    @Override
    public List<String> measure() {
        heapSize = getHeapSize();
        nonHeapSize = getNonHeapSize();
        return Arrays.asList(String.valueOf(heapSize/ 1000 / 1000), String.valueOf(nonHeapSize/ 1000 / 1000));
    }

    @Override
    public List<String> getHeaders() {
        return Arrays.asList("HeapSize(MB)", "NonHeapSize(MB)");
    }

    private Long getHeapSize() {
        CompositeDataSupport heap = getMemory("HeapMemoryUsage");
        return heap == null ? 0L : (Long) heap.get("used");
    }

//    private Long getMaxHeapSize() {
//        CompositeDataSupport heap = getMemory("HeapMemoryUsage");
//        return heap == null ? 0L : (Long) heap.get("max");
//    }

    private Long getNonHeapSize() {
        CompositeDataSupport heap = getMemory("NonHeapMemoryUsage");
        return heap == null ? 0L : (Long) heap.get("used");
    }

    private CompositeDataSupport getMemory(String pool) {
        CompositeDataSupport memoryPool = null;
        try {
            memoryPool = (CompositeDataSupport) connection.getAttribute(new ObjectName("java.lang:type=Memory"), pool);
        } catch (Exception e) {
            e.printStackTrace();

        }
        return memoryPool;
    }

}
