package org.jboss.qa.hornetq.tools;

import org.jboss.logging.Logger;
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

import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

/**
 * Start script create_memory_cpu_csv.sh in resources directory.
 * <p/>
 * Created by mnovak on 11/5/14.
 */
public class MemoryCpuMeasuring {

    private static final Logger log = Logger.getLogger(MemoryCpuMeasuring.class);

    Process p = null;

    private long pid;

    private String prefixForCsvPngFiles;

    public MemoryCpuMeasuring(long pid, String prefixForCsvPngFiles) {

        this.pid = pid;

        this.prefixForCsvPngFiles = prefixForCsvPngFiles;
    }

    public void startMeasuring() {

        File csvFiles = new File(".");

        for (File f : csvFiles.listFiles()) {

            if (f.getName().endsWith("csv") && f.getName().contains(prefixForCsvPngFiles)) {

                log.info("Delete file: " + f.getName());

                f.delete();
            }
        }

        // start create_memory_cpu_csv.sh
        log.info("Start measuring process: " + pid + " and store info to files with prefix: " + prefixForCsvPngFiles);

        ProcessBuilder processBuilder = new ProcessBuilder("src/test/resources/create_memory_cpu_csv.sh", String.valueOf(pid), prefixForCsvPngFiles);

        try {

            p = processBuilder.start();

        } catch (IOException e) {

            log.error("Fail during measuring: ", e);

            if (p != null) {

                p.destroy();

            }
        }
    }

    public void stopMeasuringAndGenerateMeasuring() throws Exception {

        if (p != null) {

            p.destroy();

        } else {

            log.error("You have to start measuring before stopping it. Process is null.");

        }

        generatePng();

    }

    public void generatePng() throws Exception {

        File dir = new File(".");

        for (File csvFile : dir.listFiles()) {

            if (csvFile.getName().contains(prefixForCsvPngFiles) && csvFile.getName().endsWith(".csv")) {

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

            }
        }
    }

    public static void main(String[] args) throws Exception {
        MemoryCpuMeasuring m = new MemoryCpuMeasuring(16329, "myserver");
        m.startMeasuring();
        Thread.sleep(30000);
        m.stopMeasuringAndGenerateMeasuring();
        m.generatePng();
    }


}
