package org.jboss.qa.hornetq.apps.servlets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.jboss.qa.hornetq.constants.Constants.FAILURE_TYPE;

/**
 * Servlet is used to kill server. Or do other stuff with the server.
 *
 * @author mnovak
 */
public class GCPauseServlet extends HttpServlet {

    // Logger
    private static final Logger log = Logger.getLogger(GCPauseServlet.class.getName());

    /**
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     * @see {@link HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)}
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     * @see {@link HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)}
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }


    /**
     * Process requests
     *
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        PrintWriter out = response.getWriter();
        String op = request.getParameter("op");
        try {

            if (op != null) {
                if (op.equalsIgnoreCase(FAILURE_TYPE.GC_PAUSE.toString())) {
                    String gcPauseTime = request.getParameter("duration");
                    causeGCPause(Long.valueOf(gcPauseTime));
                } else {
                    out.println("Operation: " + op + " is not supoported.");
                }
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, e.getMessage(), e);
            throw new IOException(e);
        } finally {
            out.close();
        }
    }

    private void causeGCPause(long gcPauseDuration) throws IOException {

        log.info("Causing GC pause.");

        // cause GC pause
        startDemonstration(gcPauseDuration);

        log.info("GC pause finished - and took ");
    }

    // object size in bytes
    private static final int DEFAULT_OBJECTSIZE = 100;
    private static int objectSize = DEFAULT_OBJECTSIZE;
    // Number of objects to fill half of the available memory with (permanent) live objects
    // The result of this calculation is not a constant with different garbage collectors.
    // Therefore, it is preferable to set this value as an explicit parameter to the benchmark.
    // Explicit setting is required if objectsize is different from default
    private static long numLive = (Runtime.getRuntime().maxMemory() / objectSize / 5);

    /**
     */
    public void startDemonstration(long gcPauseDuration) {

        // run exactly 8 mutator threads
        // which is little compared to typical web applications under load
        // and benign when several JVMs run on the same hardware (coexistence test)
        new Thread(new GarbageProducer(60, numLive / 2)).start();
        new Thread(new GarbageProducer(60, numLive / 4)).start();
        new Thread(new GarbageProducer(60, numLive / 8)).start();
        new Thread(new GarbageProducer(60, numLive / 16)).start();
        new Thread(new GarbageProducer(30, numLive / 32)).start();
        new Thread(new GarbageProducer(30, numLive / 64)).start();
        new Thread(new GarbageProducer(30, numLive / 128)).start();
        new Thread(new GarbageProducer(30, numLive / 128)).start();

        // Standard test run takes 1800 seconds (which is not enough to finish warmup and reach a true stationary state)
        // Therefore I also ran some tests for 7200 seconds (which lowers average throughput a bit)
        try {
            Thread.sleep(gcPauseDuration);
        } catch (InterruptedException iexc) {
            iexc.printStackTrace();
        }
        System.exit(0);
    }

    /**
     * Create a character array of a given length
     *
     * @param length
     * @return the character array
     */
    private static char[] getCharArray(int length) {
        char[] retVal = new char[length];
        for (int i = 0; i < length; i++) {
            retVal[i] = 'a';
        }
        return retVal;
    }

    /**
     * A garbage producer implementation
     */
    public static class GarbageProducer implements Runnable {

        // the fraction of newly created objects that do not become garbage immediately but are stored in the liveList
        int fractionLive;
        // the size of the liveList
        long myNumLive;

        /**
         * Each GarbageProducer creates objects that become garbage immediately (lifetime=0) and
         * objects that become garbage only after a lifetime>0 which is distributed about an average lifetime.
         * This average lifetime is a function of fractionLive and numLive
         *
         * @param fractionLive
         * @param numLive
         */
        public GarbageProducer(int fractionLive, long numLive) {
            this.fractionLive = fractionLive;
            this.myNumLive = numLive;
        }

        public void run() {
            int osize = objectSize;
            char[] chars = getCharArray(objectSize);
            List<String> liveList = new ArrayList<String>((int) myNumLive);
            // initially, the lifeList is filled
            for (int i = 0; i < myNumLive; i++) {
                liveList.add(new String(chars));
            }
            int counter = 0;
            int limit = 3000;
            while (true) {
                // create the majority of objects as garbage and count them
                for (int i = 0; i < fractionLive; i++) {
                    String garbage = new String(chars);
                    counter++;
                }
                // waste CPU cycles for useless String comparisons
                for (int i = 0; i < 200 * fractionLive; i++) {
                    String el = liveList.get(i);
                    if (!el.equals(chars)) {
                        continue;
                    }
                }

                // keep the fraction of objects live by placing them in the list (at a random index)
                int index = (int) (Math.random() * myNumLive);
                liveList.set(index, new String(chars));
                // take a sleep from time to time to reduce CPU usage below 100%
                if (++counter >= limit) {
                    counter = 0;
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException iexc) {
                        iexc.printStackTrace();
                    }
                }
            }
        }
    }
}


