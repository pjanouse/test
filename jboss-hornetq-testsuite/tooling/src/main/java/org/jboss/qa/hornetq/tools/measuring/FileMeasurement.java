package org.jboss.qa.hornetq.tools.measuring;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Created by mstyk on 4/13/16.
 */
public class FileMeasurement implements Measurable {

    private MBeanServerConnection connection;

    private int openFiles = 0;

    public FileMeasurement(MBeanServerConnection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("mBeanServerConnection provived to class FileMeasurement was null");
        }
        this.connection = connection;
    }

    @Override
    public List<String> measure() {
        openFiles = getOpenFiles();
        return Arrays.asList(String.valueOf(openFiles));
    }

    @Override
    public List<String> getHeaders() {
        return Arrays.asList("FileDescriptors");
    }

    private int getOpenFiles() {
        Number value = -1;
        try {
            value = (Number) connection.getAttribute(new ObjectName("java.lang:type=OperatingSystem"), "OpenFileDescriptorCount");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value.intValue();
    }

}
