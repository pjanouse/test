package org.jboss.qa.hornetq.tools.measuring;

import org.jboss.qa.hornetq.*;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.tools.CSVDataSeriesReader;
import org.jboss.qa.hornetq.tools.ContainerUtils;
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
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;


/**
 * Class designed to measure EAP server resources during cli tests. Heap-size, Non-heap-size, Thread-count and
 * Peak-thread-count attributes are monitored.
 *
 * @author Petr Kremensky pkremens@redhat.com on 4/29/15.
 * @author Martin Styk mstyk@redhat.com on 12/04/16.
 */
public class Measure extends Thread {

    protected static Logger LOGGER = Logger.getLogger(Measure.class.getName());
    private static final int DEFAULT_CHECK_PERIOD = 60 * 1000;

    private final MBeanServerConnection connection;
    private final JMXConnector jmxConnector;

    private long startTime;
    private int intervalBetweenMeasurements;

    private boolean stop = false;
    private String outFileNamingPattern;

    private PrintWriter writer;
    private File csvFile;

    private List<Measurable> measurableList = new ArrayList<Measurable>(5);

    public Measure(Container container, String outFileNamingPattern) throws Exception {

        this("service:jmx:remote+http://" + container.getHostname() + ":" + container.getPort(),container.getProcessId(),outFileNamingPattern);
    }


    /**
     * Creates measurement thread
     *
     * @param protocol             "service:jmx:remoting-jmx:// for EAP6, "service:jmx:remote+http:// for EAP7
     * @param host                 host to connect
     * @param port                 port to connect
     * @param processID            PID of monitored process
     * @param outFileNamingPattern pattern of name file to write output
     * @throws Exception
     */
    public Measure(String protocol, String host, int port, int processID, String outFileNamingPattern) throws Exception {
        this(System.getProperty("jmx.service.url", protocol + "//" + host + ":" + port), processID, outFileNamingPattern);
    }


    /**
     * Creates measurement thread
     *
     * @param protocol             "service:jmx:remoting-jmx:// for EAP6, "service:jmx:remote+http:// for EAP7
     * @param host                 host to connect
     * @param port                 port to connect
     * @param processID            PID of monitored process
     * @param outFileNamingPattern pattern of name file to write output
     * @throws Exception
     */
    public Measure(String protocol, String host, int port, int processID, String outFileNamingPattern, int checkPeriod) throws Exception {
        this(System.getProperty("jmx.service.url", protocol + "//" + host + ":" + port), processID, outFileNamingPattern);
    }


    public Measure(String urlString, int processID, String outFileNamingPattern) throws Exception {
        this(urlString, processID, outFileNamingPattern, DEFAULT_CHECK_PERIOD);
    }

    /**
     * @param urlString
     * @param processID            PID of monitored process
     * @param outFileNamingPattern file to write output
     * @throws Exception
     */
    public Measure(String urlString, int processID, String outFileNamingPattern, int checkPeriod) throws Exception {
        if (urlString == null || urlString.isEmpty()) {
            throw new IllegalArgumentException("urlString is " + urlString);
        }
        if (outFileNamingPattern == null || outFileNamingPattern.isEmpty()) {
            throw new IllegalArgumentException("outFileNamingPattern is " + outFileNamingPattern);
        }

        this.intervalBetweenMeasurements = checkPeriod;
        this.outFileNamingPattern = outFileNamingPattern;
        this.csvFile = new File(outFileNamingPattern + ".csv");
        JMXServiceURL serviceURL = new JMXServiceURL(urlString);
        jmxConnector = JMXConnectorFactory.connect(serviceURL, null);
        connection = jmxConnector.getMBeanServerConnection();

        measurableList.add(new CpuMeasurement(connection));
        measurableList.add(new ThreadMeasurement(connection));
        measurableList.add(new MemoryMeasurement(connection));
        measurableList.add(new SocketMeasurement(processID));
        measurableList.add(new FileMeasurement(connection));

    }

    public void run() {

        startTime = System.currentTimeMillis();

        try {

            writer = new PrintWriter(csvFile);
            writeHeader();

            while (!stop) {
                performOneMeasurement();
                Thread.sleep(intervalBetweenMeasurements);
            }
        } catch (Exception ex) {

            LOGGER.warn(ex.toString());

        } finally {
            if (writer != null) {
                writer.flush();
                writer.close();
            }

            if (jmxConnector != null) {
                try {
                    jmxConnector.close();
                } catch (IOException e) {
                    //ignore
                }
            }
        }

        generateAllPngs();

    }


    public void stopMeasuring() {
        stop = true;
    }


    /**
     * Get the latest measured values.
     *
     * @return Report of measured values.
     */
    public String getReport(String... reports) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("*******************************************************************").append("\n");
        sb.append("Current Time:      ").append(Calendar.getInstance().getTime()).append("\n");
        sb.append("Uptime:            ").append(getUptime()).append("\n");

        for (String rep : reports) {
            sb.append(rep);
        }

        sb.append("*******************************************************************");
        return sb.toString();
    }


    private String getUptime() {
        long millis = System.currentTimeMillis() - startTime;
        return String.format("%d hr, %d min, %d sec",
                TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis)),
                TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)
                ));
    }

    private void writeHeader() {
        StringBuilder sb = new StringBuilder();
        sb.append("Duration,");
        for (Measurable m : measurableList) {
            List<String> headers = m.getHeaders();
            for (String s : headers) {
                sb.append(s).append(",");
            }
        }
        sb.deleteCharAt(sb.lastIndexOf(","));
        writer.println(sb.toString());
    }


    private void performOneMeasurement() {
        StringBuilder sb = new StringBuilder();
        sb.append(System.currentTimeMillis()).append(",");
        for (int j = 0; j < measurableList.size(); j++) {
            List<String> results = measurableList.get(j).measure();
            for (int i = 0; i < results.size(); i++) {
                sb.append(results.get(i));
                if (i != results.size() - 1 || j != measurableList.size() - 1) {
                    sb.append(",");
                }
            }
        }
        writer.println(sb.toString());
    }


    private void generateAllPngs() {
        for (Measurable m : measurableList) {
            try {
                if (m.getClass().getSimpleName().equals(CpuMeasurement.class.getSimpleName())) {
                    generatePng(Arrays.asList(CpuMeasurement.CPU_LOAD_PROC_HEADER, CpuMeasurement.CPU_LOAD_SYS_HEADER), new File(outFileNamingPattern + m.getClass().getSimpleName() + "Load.png"));
                    generatePng(Arrays.asList(CpuMeasurement.CPU_TIME_PROC_HEADER), new File(outFileNamingPattern + m.getClass().getSimpleName() + "Time.png"));
                } else {
                    generatePng(m.getHeaders(), new File(outFileNamingPattern + m.getClass().getSimpleName() + ".png"));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * File must be in format:
     * Time, valuex, valuey, valuez, ...
     * <p>
     * It will generate png file where x axis is time and y axis is valuex valuey valuez
     *
     * @param outputPng output file with data
     * @throws Exception
     */
    private void generatePng(List<String> columns, File outputPng) throws Exception {


        if (csvFile.getName().endsWith(".csv")) {

            CSVDataSeriesReader csvDataSeriesReader = new CSVDataSeriesReader(',');

            List<XYSeries> data = csvDataSeriesReader.readDataset(new FileReader(csvFile), columns);

            XYSeriesCollection my_data_series = new XYSeriesCollection();

            for (XYSeries xySeries : data) {

                my_data_series.addSeries(xySeries);

            }

            JFreeChart XYLineChart = ChartFactory.createXYLineChart("Chart", "Time", "Value", my_data_series, PlotOrientation.VERTICAL, true, false, false);

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

            if (outputPng.exists()) {

                outputPng.delete();

            }

            ChartUtilities.saveChartAsPNG(outputPng, XYLineChart, width, height);

        } else {
            throw new Exception("File " + csvFile.getName() + " must have .csv suffix.");
        }
    }


}
