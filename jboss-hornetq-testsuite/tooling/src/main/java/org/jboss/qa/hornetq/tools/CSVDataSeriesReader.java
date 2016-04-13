package org.jboss.qa.hornetq.tools;

import org.jfree.data.xy.XYSeries;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * WARNING:
 * FIRST COLUMN IN CSV MUST BE TIME WILL BE X AXIS FOR OTHER VALUES IN NEXT COLUMNS.
 * <p>
 * A utility class for reading from a CSV file.
 * This initial version is very basic, and won't handle errors in the data
 * file very gracefully.
 *
 * @author mnovak@redhat.com
 */
public class CSVDataSeriesReader {

    /**
     * The field delimiter.
     */
    private char fieldDelimiter;

    /**
     * Creates a new CSV reader where the field delimiter is a comma, and the
     * text delimiter is a double-quote.
     */
    public CSVDataSeriesReader() {
        this(',');
    }

    /**
     * Creates a new reader with the specified field and text delimiters.
     *
     * @param fieldDelimiter the field delimiter (usually a comma, semi-colon,
     *                       colon, tab or space).
     */
    public CSVDataSeriesReader(char fieldDelimiter) {
        this.fieldDelimiter = fieldDelimiter;
    }

    /**
     * Reads a {@link XYSeries} from a CSV file or input source.
     *
     * @param in the input source.
     * @return A category dataset.
     * @throws IOException if there is an I/O problem.
     */
    public List<XYSeries> readDataset(Reader in) throws IOException {

        BufferedReader reader = new BufferedReader(in);
        List<String> columnKeys = null;
        int lineIndex = 0;
        String line = reader.readLine();
        List<XYSeries> dataset = new ArrayList<XYSeries>();
        while (line != null) {
            // trim field delimiter if in the end of start of line
            line = trimFieldDelimiter(line);
//            System.out.println("Print line after trim: " + line);
            if (lineIndex == 0) {  // first line contains column keys
                columnKeys = extractColumnKeys(line);
                for (String c : columnKeys) {
                    dataset.add(new XYSeries(c));
                }
            } else {  // remaining lines contain a row key and data values
                extractRowKeyAndData(line, dataset);
            }
            line = reader.readLine();
            lineIndex++;
        }

//        int j = 0;
//        for (XYSeries xySeries : dataset) {
//            System.out.print("Values for column: " + columnKeys.get(j));
//            for (int i = 0; i < xySeries.getItemCount(); i++) {
//                System.out.println("x: " + xySeries.getDataItem(i).getXValue() + " y: " + xySeries.getDataItem(i).getYValue());
//            }
//            System.out.println();
//            j++;
//        }

        return dataset;

    }


    /**
     * Reads a {@link XYSeries} from a CSV file or input source.
     *
     * @param in the input source.
     * @return A category dataset.
     * @throws IOException if there is an I/O problem.
     */
    public List<XYSeries> readDataset(Reader in, List<String> keys) throws IOException {

        BufferedReader reader = new BufferedReader(in);
        List<String> columnKeys = null;
        List<Integer> rightColumnList = null;
        int lineIndex = 0;
        String line = reader.readLine();
        List<XYSeries> dataset = new ArrayList<XYSeries>();
        while (line != null) {
            // trim field delimiter if in the end of start of line
            line = trimFieldDelimiter(line);
//            System.out.println("Print line after trim: " + line);
            if (lineIndex == 0) {  // first line contains column keys
                columnKeys = extractColumnKeys(line);
                rightColumnList = getRightCollumns(keys, columnKeys);
                for (String c : keys) {
                    dataset.add(new XYSeries(c));
                }
            } else {  // remaining lines contain a row key and data values
                extractRowKeyAndData(line, dataset, rightColumnList);
            }
            line = reader.readLine();
            lineIndex++;
        }
        return dataset;

    }

    private List<Integer> getRightCollumns(List<String> keys, List<String> columnKeys) {
        List<Integer> res = new ArrayList<Integer>();
        for (int i = 0; i < columnKeys.size(); i++) {
            for (int j = 0; j < keys.size(); j++) {
                if (columnKeys.get(i).equals(keys.get(j))) {
                    res.add(i);
                    break;
                }
            }
        }
        return res;
    }

    private String trimFieldDelimiter(String line) {
        StringBuilder stringBuilder = new StringBuilder(line);
        if (String.valueOf(stringBuilder.charAt(0)).equals(String.valueOf(fieldDelimiter))) {
            stringBuilder.deleteCharAt(0);
        }
        if (String.valueOf(stringBuilder.charAt(stringBuilder.length() - 1)).equals(String.valueOf(fieldDelimiter))) {
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        }
        return stringBuilder.toString();
    }

    /**
     * Extracts the column keys from a string.
     *
     * @param line a line from the input file.
     * @return A list of column keys.
     */
    private List<String> extractColumnKeys(String line) {
        StringTokenizer str = new StringTokenizer(line, String.valueOf(fieldDelimiter));
        List<String> keys = new ArrayList<String>();
        str.nextToken(); // first is time - trim it
        while (str.hasMoreTokens()) {
            keys.add(str.nextToken());
        }
//        for (String s : keys) {
//            System.out.println("Key:" + s);
//        }
        return keys;
    }

    /**
     * Put data to XY serris
     * - first is time - x axis
     * - second is value - y axis
     *
     * @param line    the line from the input source.
     * @param dataset the dataset to be populated.
     */

    private void extractRowKeyAndData(String line,
                                      List<XYSeries> dataset) {
        StringTokenizer tokenizer = new StringTokenizer(line, String.valueOf(fieldDelimiter));

        // for each token add pair to XYSeries - time + value
        // remember first column as it is time
        long time = Long.valueOf(tokenizer.nextToken());
        for (XYSeries xySeries : dataset) {
            xySeries.add(Double.valueOf(time), Double.valueOf(tokenizer.nextToken()));
        }
    }

    private void extractRowKeyAndData(String line,
                                      List<XYSeries> dataset, List<Integer> rightCollumn) {
        String[] tokens = line.split(String.valueOf(fieldDelimiter));

        // for each token add pair to XYSeries - time + value
        // remember first column as it is time
        Double time = Double.valueOf(tokens[0]);
        for (XYSeries xySeries : dataset) {


        }
        for(int i=0; i< dataset.size() ;i++){
            dataset.get(i).add(Double.valueOf(time), Double.valueOf(tokens[rightCollumn.get(i)+1]));
        }
    }

}
