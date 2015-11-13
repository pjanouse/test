package org.jboss.qa.hornetq.tools;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

/**
 * Created by mnovak on 11/3/15.
 */
public class HighCPUUtils {

    public static Process generateLoadInSeparateProcess() throws Exception {

        JavaProcessBuilder javaProcessBuilder = new JavaProcessBuilder();
        javaProcessBuilder.addClasspathEntry(System.getProperty("java.class.path"));
        javaProcessBuilder.setWorkingDirectory(new File(".").getAbsolutePath());
        javaProcessBuilder.setMainClass(CpuLoadGenerator.class.getName());
        Process process = javaProcessBuilder.startProcess();
        return process;
    }
}

