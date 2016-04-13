package org.jboss.qa.hornetq.tools.measuring;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import java.util.Arrays;
import java.util.List;

/**
 * Created by mstyk on 4/12/16.
 */
public class CpuMeasurement implements Measurable {

    protected static final String CPU_LOAD_PROC_HEADER = "Process CPU load";
    protected static final String CPU_LOAD_SYS_HEADER = "System CPU load";
    protected static final String CPU_TIME_PROC_HEADER = "Process CPU time in last interval(ms)";

    private MBeanServerConnection connection;

    private double processCpuLoad = 0d;
    private double systemCpuLoad = 0d;
    private Long processCpuTime = 0L;
    private Long oldProcessCpuTime = 0L;

    public CpuMeasurement(MBeanServerConnection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("mBeanServerConnection prived to class CpuMeasurement was null");
        }
        this.connection = connection;
    }

    @Override
    public List<String> measure() {
        processCpuLoad = getProcessCpuLoad();
        systemCpuLoad = getSystemCpuLoad();
        processCpuTime = getProcessCpuTime();
        Long cpuTimeDiff = oldProcessCpuTime == 0 ? 0 : processCpuTime - oldProcessCpuTime;
        computeOld();
        return Arrays.asList(String.valueOf(processCpuLoad), String.valueOf(systemCpuLoad), String.valueOf(cpuTimeDiff / 1000000)); //to ms
    }

    @Override
    public List<String> getHeaders() {
        return Arrays.asList(CPU_LOAD_PROC_HEADER, CPU_LOAD_SYS_HEADER, CPU_TIME_PROC_HEADER);
    }

    private Double getProcessCpuLoad() {
        return getCpu("ProcessCpuLoad").doubleValue() * 100;
    }

    private Double getSystemCpuLoad() {
        return getCpu("SystemCpuLoad").doubleValue() * 100;
    }

    private Long getProcessCpuTime() {
        return getCpu("ProcessCpuTime").longValue();
    }

    private Number getCpu(String attribute) {
        Number value = -1;
        try {
            value = (Number) connection.getAttribute(new ObjectName("java.lang:type=OperatingSystem"), attribute);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }

    private void computeOld() {
        oldProcessCpuTime = processCpuTime;
    }
}
