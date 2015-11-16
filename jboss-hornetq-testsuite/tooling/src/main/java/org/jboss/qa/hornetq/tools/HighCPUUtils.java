package org.jboss.qa.hornetq.tools;

import org.jboss.qa.hornetq.Container;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

/**
 * Created by mnovak on 11/3/15.
 */
public class HighCPUUtils {

    private static long defaultLoadDuration = 15 * 60 * 1000; // 15 min
    /**
     * Generate 100% cpu load for 15 min
     * @return process of cpu load generator
     * @throws Exception
     */
    public static Process generateLoadInSeparateProcess() throws Exception {
        return generateLoadInSeparateProcess(defaultLoadDuration);
    }

    public static Process generateLoadInSeparateProcess(long durationOfLoad) throws Exception {

        JavaProcessBuilder javaProcessBuilder = new JavaProcessBuilder();
        javaProcessBuilder.addClasspathEntry(System.getProperty("java.class.path"));
        javaProcessBuilder.setWorkingDirectory(new File(".").getAbsolutePath());
        javaProcessBuilder.addArgument(String.valueOf(durationOfLoad));
        javaProcessBuilder.setMainClass(CpuLoadGenerator.class.getName());
        Process process = javaProcessBuilder.startProcess();
        return process;
    }


    /**
     * Bind contianer to cpu and cause 100% cpu load on it for 15 min.
     * @param container container on which to cause 100% cpu load and lower priority
     * @param cpuIds    specifies CPU cores to which EAP server and cpu load generator will be bound (for example "0-3", "0,1", ...)
     * @return return process of CPU loader tool, it can be destroyed later
     * @throws Exception
     */
    public static Process causeMaximumCPULoadOnContainer(Container container, String cpuIds) throws Exception {
        return causeMaximumCPULoadOnContainer(container,cpuIds, defaultLoadDuration);
    }

    /**
     * @param container container on which to cause 100% cpu load and lower priority of container
     * @param cpuIds    specifies CPU cores to which EAP server and cpu load generator will be bound (for example "0-3", "0,1", ...)
     * @return return process of CPU loader tool, it can be destroyed later
     * @throws Exception
     */
    public static Process causeMaximumCPULoadOnContainer(Container container, String cpuIds, long durationOfLoad) throws Exception {

        long containerPid = ProcessIdUtils.getProcessId(container);
        BindProcessToCpuUtils.bindProcessToCPU(String.valueOf(containerPid), cpuIds);
        ProcessIdUtils.setPriorityToProcess(String.valueOf(containerPid), 19);

        Process process = generateLoadInSeparateProcess(durationOfLoad);
        int highCpuLoaderPid = ProcessIdUtils.getProcessId(process);
        BindProcessToCpuUtils.bindProcessToCPU(String.valueOf(highCpuLoaderPid), cpuIds);

        return process;
    }


}

