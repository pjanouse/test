package org.jboss.qa.hornetq.tools;

import org.apache.log4j.Logger;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.Range;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.awt.*;
import java.io.*;
import java.util.List;


public class MemoryMeasuring extends Thread {

    private static final Logger log = Logger.getLogger(MemoryCpuMeasuring.class);

    String host;
    String url;
    File outCsvFile;
    MBeanServerConnection mbeanConn;
    JMXConnector jmxConnector;
    boolean stop = false;
    private long intervalBetweenMeasurements = 60000;
    PrintWriter writer;
    long startTime;

    /**
     * Connects to JMX to EAP 6 server to localhost:9999
     *
     * @param outCsvFile file where data will be printed
     * @throws Exception
     */
    public MemoryMeasuring(File outCsvFile) throws Exception {
        this("localhost", "9999", outCsvFile);
    }

    /**
     * Connects to JMX to EAP 6 management port
     *
     * @param hostname   hostname of management interface
     * @param port       port where management port is listening
     * @param outCsvFile file where data will be printed
     * @throws Exception
     */
    public MemoryMeasuring(String hostname, String port, File outCsvFile) throws Exception {

        this.outCsvFile = outCsvFile;
        if (outCsvFile.exists()) {
            outCsvFile.delete();
        }
        outCsvFile.createNewFile();

        host = System.getProperty("jmx.host", hostname);
        port = System.getProperty("jmx.port", port);
        url = "service:jmx:remoting-jmx://" + host + ":" + port;
        JMXServiceURL serviceUrl = new JMXServiceURL(url);
        try {
            jmxConnector = JMXConnectorFactory.connect(serviceUrl, null);
        } catch (Exception e) {
            Thread.sleep(5000);
            jmxConnector = JMXConnectorFactory.connect(serviceUrl, null);
        }
        mbeanConn = jmxConnector.getMBeanServerConnection();
    }

    public void run() {

        startTime = System.currentTimeMillis();

        try {

            writer = new PrintWriter(outCsvFile);
            writer.println("Time, Young Gen, Tenured Gen, Perm Gen, Old Gen, Total");

            while (!stop) {
                performOneMemoryMeasurement();
                Thread.sleep(intervalBetweenMeasurements);
            }
        } catch (Exception ex)  {

            log.error(ex);

        } finally {
            if (writer != null) {
                writer.flush();
                writer.close();
            }

            if (jmxConnector != null)   {
                try {
                    jmxConnector.close();
                } catch (IOException e) {
                    //ignore
                }
            }
        }
    }

    public void stopMeasuring() {
        stop = true;
    }

    public double performOneMemoryMeasurement() throws Exception {

        CompositeDataSupport edenSpace;
        try {
            edenSpace = (CompositeDataSupport) mbeanConn.getAttribute(new ObjectName("java.lang:type=MemoryPool,name=PS Eden Space"), "Usage");
        } catch (InstanceNotFoundException ex) {
            edenSpace = (CompositeDataSupport) mbeanConn.getAttribute(new ObjectName("java.lang:type=MemoryPool,name=Eden Space"), "Usage");
        }
        Long edenSpaceUsed = (Long) edenSpace.get("used");

        CompositeDataSupport oldGen;
        try {
            oldGen = (CompositeDataSupport) mbeanConn.getAttribute(new ObjectName("java.lang:type=MemoryPool,name=PS Old Gen"), "Usage");
        } catch (InstanceNotFoundException ex) {
            oldGen = (CompositeDataSupport) mbeanConn.getAttribute(new ObjectName("java.lang:type=MemoryPool,name=Tenured Gen"), "Usage");
        }
        Long oldGenUsed = (Long) oldGen.get("used");

        CompositeDataSupport permGen;
        try {
            permGen = (CompositeDataSupport) mbeanConn.getAttribute(new ObjectName("java.lang:type=MemoryPool,name=PS Perm Gen"), "Usage");
        } catch (InstanceNotFoundException ex) {
            try {
                permGen = (CompositeDataSupport) mbeanConn.getAttribute(new ObjectName("java.lang:type=MemoryPool,name=Perm Gen"), "Usage");
            } catch (InstanceNotFoundException probablyJava8) {
                // Java 8 doesn't have perm gen, it has meta space
                permGen = (CompositeDataSupport) mbeanConn.getAttribute(new ObjectName("java.lang:type=MemoryPool,name=Metaspace"), "Usage");
            }
        }
        Long permGenUsed = (Long) permGen.get("used");

        CompositeDataSupport survivorSpace;
        try {
            survivorSpace = (CompositeDataSupport) mbeanConn.getAttribute(new ObjectName("java.lang:type=MemoryPool,name=PS Survivor Space"), "Usage");
        } catch (InstanceNotFoundException ex) {
            survivorSpace = (CompositeDataSupport) mbeanConn.getAttribute(new ObjectName("java.lang:type=MemoryPool,name=Survivor Space"), "Usage");
        }
        Long survivorSpaceUsed = (Long) survivorSpace.get("used");

        log.debug("Eden space: " + edenSpaceUsed + ", ");
        log.debug("Old gen: " + oldGenUsed + ", ");
        log.debug("Perm gen: " + permGenUsed + ", ");
        log.debug("Survivor space: " + survivorSpaceUsed + ", ");
        Long total = edenSpaceUsed + oldGenUsed + permGenUsed + survivorSpaceUsed;
        log.debug("***************************************** Memory Measuring: " + total + " **************************");
        // Time, Young Gen, Tenured Gen, Perm Gen, Old Gen, Total
        writer.print(System.currentTimeMillis() - startTime + ", " + edenSpaceUsed + ", "
                + survivorSpaceUsed + ", " + permGenUsed + ", " + oldGenUsed + ", " + total + ",");
        writer.println();
        writer.flush();

        return total;
    }

    public void forceGC() throws Exception {
        Object ret = mbeanConn.invoke(new ObjectName("java.lang:type=Memory"), "gc", null, null);
    }

    /**
     * Set interval between calling JMX to measure memory
     *
     * @param intervalBetweenMeasurements in ms (default is 60000 ms)
     */
    public void setIntervalBetweenMeasurements(long intervalBetweenMeasurements) {
        this.intervalBetweenMeasurements = intervalBetweenMeasurements;
    }

    /**
     * File must be in format:
     * Time, valuex, valuey, valuez, ...
     * <p>
     * It will generate png file where x axis is time and y axis is valuex valuey valuez
     *
     * @param csvFile csvFile with data
     * @throws Exception
     */
    public void generatePng(File csvFile) throws Exception {


        if (csvFile.getName().endsWith(".csv")) {

            CSVDataSeriesReader csvDataSeriesReader = new CSVDataSeriesReader(',');

            List<XYSeries> data = csvDataSeriesReader.readDataset(new FileReader(csvFile));

            XYSeriesCollection my_data_series = new XYSeriesCollection();

            for (XYSeries xySeries : data) {

                my_data_series.addSeries(xySeries);

            }

            JFreeChart XYLineChart = ChartFactory.createXYLineChart("Memory chart", "Time", "Memory", my_data_series, PlotOrientation.VERTICAL, true, false, false);

            //// main colors
            XYLineChart.setBackgroundPaint(Color.white);

            final XYPlot plot = (XYPlot) XYLineChart.getPlot();

            plot.setBackgroundPaint(Color.lightGray);

            plot.setRangeGridlinePaint(Color.white);

            //// customise the range (Y) axis...
            final NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();

            rangeAxis.setAutoRangeIncludesZero(true);

            rangeAxis.setLowerMargin(0.1);

            rangeAxis.setUpperMargin(0.1);

            //// customise the domain (X) axis...
            final NumberAxis domainAxis = (NumberAxis) plot.getDomainAxis();

            domainAxis.setUpperMargin(0.01);

            domainAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

            Range origRange = domainAxis.getRange();

            domainAxis.setRange(Range.expand(origRange, 0.02, 0.02));

            //// RENDERER
            final XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();

            renderer.setDrawOutlines(true);

            //// ADD-ONS
            renderer.setBaseShapesVisible(true);

            renderer.setDrawOutlines(true);

            renderer.setUseFillPaint(true);

            renderer.setBaseFillPaint(Color.yellow);

            int width = 1600; /* Width of the image */

            int height = 1200; /* Height of the image */

            File outputPng = new File(csvFile.getName().replaceAll(".csv", ".png"));
            if (outputPng.exists()) {

                outputPng.delete();

            }

            ChartUtilities.saveChartAsPNG(outputPng, XYLineChart, width, height);

        }  else {
            throw new Exception("File " + csvFile.getName() + " must have .csv suffix.");
        }
    }

    public static void main(String[] args) throws Exception {

        File f = new File("memory.csv");

        MemoryMeasuring m = new MemoryMeasuring(f);

        m.setIntervalBetweenMeasurements(1000);

        m.start();
        Thread.sleep(60000);
        m.stopMeasuring();
        m.join();
        m.generatePng(f);


    }

}
