package org.jboss.qa.hornetq.tools.measuring;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Created by mstyk on 4/13/16.
 */
public class SocketMeasurement implements Measurable {

    private int processId;
    private String[] command;

    private int openSockets = 0;

    public SocketMeasurement(int processId) {
        this.processId = processId;
        command = new String[]{"/bin/sh", "-c", " lsof -p " + processId + " | grep TCP | wc -l"};
    }

    @Override
    public List<String> measure() {
        openSockets = getOpenSockets();
        return Arrays.asList(String.valueOf(openSockets));
    }

    @Override
    public List<String> getHeaders() {
        return Arrays.asList("OpenSockets");
    }

    private int getOpenSockets() {
        int result = -1;
        String value = null;
        try {
            value = execCmd(command);
            result = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            Measure.LOGGER.error("Cannot parse output of command. Output was not parseable to integer: " + value);
        } catch (IOException e) {
            Measure.LOGGER.error("Cannot read output of command.");
        } catch (Exception e) {
            Measure.LOGGER.error("ERROR: " + e);
        }
        return result;
    }

    public static String execCmd(String[] cmd) throws IOException, InterruptedException {
        Process proc = Runtime.getRuntime().exec(cmd);
        InputStream is = proc.getInputStream();
        Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        String val = null;
        if (s.hasNext()) {
            val = s.next();
        }
        return val;
    }

}
