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
        startDemonstration();

        log.info("GC pause finished - and took ");
    }

    public static void forceGC(long gcPauseDuration) {
        log.info("#test forceGC");
        CountDownLatch finalized = new CountDownLatch(1);
        WeakReference<DumbReference> dumbReference = new WeakReference<DumbReference>(new DumbReference(finalized, gcPauseDuration));

        // A loop that will wait GC, using the minimal time as possible
        while (!(dumbReference.get() == null && finalized.getCount() == 0)) {
            System.gc();
            System.runFinalization();
            try {
                finalized.await(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
            }
        }
        log.info("#test forceGC Done");
    }

    protected static class DumbReference {

        private CountDownLatch finalized;
        private long sleep;

        public DumbReference(CountDownLatch finalized, long sleep) {
            this.finalized = finalized;
            this.sleep = sleep;
        }

        public void finalize() throws Throwable {
            log.info("Causing GC pause.");
            long startTime = System.currentTimeMillis();
            finalized.countDown();
            Thread.sleep(sleep);
            super.finalize();
            log.info("GC pause finished - and took: " + (System.currentTimeMillis() - startTime) + " ms");
        }
    }

    public static void startDemonstration() {

        StringBuffer buffer = new StringBuffer();
        long t0 = 0;
        int capacity = 10000;
        List<String> strings = new ArrayList<String>(capacity);


		/* Build a really big string */
        for (int i = 0; i < 1000; i++) {
            buffer.append("This is an unneccessarily long string...But you know why\n");
        }


        for (int i = 0; i < capacity; i++) {
            /* increase memory usage */
            strings.add("randomString:" + Math.random() + buffer);

          		/* Generate some garbage */
            for (int j = 0; j < 100; j++) {
                strings.set((int) Math.floor(Math.random() * strings.size()),
                        "randomString:" + Math.random() + buffer);
            }

			/* Print the memory usage every now and then */
            if (i % 100 == 0) {
                long t1 = System.currentTimeMillis();
                if (t1 - t0 > 100) {
                    t0 = t1;
                    System.out.println("it=" + pad(String.valueOf(i), 4) + memString());
                }
            }
        }
    }

    public static String pad(String str, int i) {
        while (str.length() < i) {
            str = " " + str;
        }
        return str;
    }


    private static String memString() {

        StringBuffer buffer = new StringBuffer();
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType().equals(MemoryType.HEAP)) {
                buffer.append(" - ")
                        .append(pool.getName())
                /* usage includes unreachable objects that are not collected yet */
                        .append(": u=").append(toString(pool.getUsage()))
				/* usage as of right after the last collection */
                        .append(" cu=").append(toString(pool.getCollectionUsage()));

            }
        }
        return buffer.toString();
    }

    private static String toString(MemoryUsage memoryUsage) {
        String string = (memoryUsage.getUsed() * 100) / memoryUsage.getMax() + "%";
        return pad(string, 4);
    }
}


