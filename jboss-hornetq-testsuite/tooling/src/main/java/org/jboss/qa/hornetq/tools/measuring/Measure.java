package org.jboss.qa.hornetq.tools.measuring;

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

import javax.management.MBeanServerConnection;
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
import java.util.List;

import org.apache.log4j.Logger;


/**
 * Class designed to measure EAP server resources during soak tests.
 *
 * @author Petr Kremensky pkremens@redhat.com on 4/29/15.
 * @author Martin Styk mstyk@redhat.com on 12/04/16.
 */
public class Measure extends Thread {

    protected static Logger LOGGER = Logger.getLogger(Measure.class.getName());

    private final MBeanServerConnection connection;
    private final JMXConnector jmxConnector;

    private long startTime;
    private int intervalBetweenMeasurements;

    private boolean stop = false;
    private String outFileNamingPattern;

    private PrintWriter writer;
    private File csvFile;

    private int processId;
    private String url;

    private List<Measurable> measurableList = new ArrayList<Measurable>(5);

    /**
     * Creates measurement thread from builder
     *
     * @throws Exception
     */
    protected Measure(Builder builder) throws Exception {
        if (builder == null) {
            throw new IllegalArgumentException("builder is null");
        }

        this.url = builder.jmxUrl == null ?
                builder.protocol + "://" + builder.host + ":" + builder.port : builder.jmxUrl;
        this.processId = builder.processId;
        this.intervalBetweenMeasurements = builder.measurePeriod;
        this.outFileNamingPattern = builder.outFileNamingPattern;
        this.csvFile = new File(outFileNamingPattern + ".csv");

        JMXServiceURL serviceURL = new JMXServiceURL(this.url);
        jmxConnector = JMXConnectorFactory.connect(serviceURL, null);
        connection = jmxConnector.getMBeanServerConnection();

        measurableList.add(new CpuMeasurement(connection));
        measurableList.add(new ThreadMeasurement(connection));
        measurableList.add(new MemoryMeasurement(connection));

        if (!System.getProperty("os.name").toString().toLowerCase().contains("win")) {
            measurableList.add(new SocketMeasurement(processId));
            measurableList.add(new FileMeasurement(connection));
        }
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

            CsvParser csvDataSeriesReader = new CsvParser();

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


    public static final class Builder {

        private String protocol;
        private String host;
        private int port = -1;
        private String jmxUrl;
        private int processId = -1;
        private String outFileNamingPattern = "server";
        private int measurePeriod = 1000;

        public Builder() {
        }

        /**
         * Defines the protocol to connect to jmx
         */
        public Builder protocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        /**
         * Defines the host name to connect
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * Defines the port of host name to connect
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Defines the URL for JMX to connect
         * by default "service:jmx:remoting-jmx for EAP6,
         * by default "service:jmx:remote+http for EAP7
         */
        public Builder jmxUrl(String jmxUrl) {
            this.jmxUrl = jmxUrl;
            return this;
        }

        /**
         * Defines PID of java process to observe
         */
        public Builder processId(int processId) {
            this.processId = processId;
            return this;
        }

        /**
         * Defines file naming pattern of output files
         * eg pattern "jms-server" will generate files jms-server.csv and jms-server-*.png
         */
        public Builder outFileNamingPattern(String outFileNamingPattern) {
            this.outFileNamingPattern = outFileNamingPattern;
            return this;
        }

        /**
         * Defines file naming pattern of output files
         * eg pattern "jms-server" will generate files jms-server.csv and jms-server-*.png
         */
        public Builder measurePeriod(int measurePeriod) {
            this.measurePeriod = measurePeriod;
            return this;
        }

        public Measure build() throws Exception {
            check();
            return new Measure(this);
        }

        private void check() {
            if (jmxUrl == null && (host == null || port == -1 || protocol == null)) {
                throw new IllegalArgumentException("At least one way to connect to JMX must be specified");
            }
            if (processId == -1) {
                throw new IllegalArgumentException("processId must be specified");
            }
        }
    }

}
