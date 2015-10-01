package org.jboss.qa.hornetq.apps.servlets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.jboss.qa.hornetq.constants.Constants.FAILURE_TYPE;

/**
 * Servlet is used to kill server. Or do other stuff with the server.
 *
 * @author mnovak
 */
public class OOMServlet extends HttpServlet {

    // Logger
    private static final Logger log = Logger.getLogger(OOMServlet.class.getName());

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
                if (op.equalsIgnoreCase(FAILURE_TYPE.OUT_OF_MEMORY_HEAP_SIZE.toString())) {
                    causeOOMHeapSize();
                } else if (op.equalsIgnoreCase(FAILURE_TYPE.OUT_OF_MEMORY_UNABLE_TO_OPEN_NEW_NATIE_THREAD.toString())) {
                    causeOOMUnableToOpenNewNativeThread();
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

    private void causeOOMHeapSize() throws IOException {

        log.info("Causing OOM on heap size.");

        List<String> list = new ArrayList<String>();
        byte[] data = new byte[1024 * 1024];
        while (true) {
            list.add(new String(data));
        }
    }

    private void causeOOMUnableToOpenNewNativeThread() throws IOException {

        log.info("Causing OOM on unable to open new native thread.");

        while (true) {
            new Thread() {
                @Override
                public void run() {
                    // just do some slow waiting
                    while (true)    {
                        try {
                            Thread.sleep(10000000);
                        } catch (InterruptedException e) {
                            // ignore
                        }
                    }
                }
            }.start();
        }
    }
}
