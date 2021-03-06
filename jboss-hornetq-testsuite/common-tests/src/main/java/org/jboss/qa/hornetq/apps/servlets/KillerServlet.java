package org.jboss.qa.hornetq.apps.servlets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servlet is used to kill server. Or do other stuff with the server.
 *
 * @author mnovak
 */
public class KillerServlet extends HttpServlet {

    // Logger
    private static final Logger log = Logger.getLogger(KillerServlet.class.getName());

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
//        out.println("op is: " + op);
        try {

            if (op != null) {
                if (op.equals("kill")) {
                    killServer(out);
                } else if (op.equals("getId")) {
                    out.println(getPid());
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

    private void killServer(PrintWriter out) throws IOException {

        long pid = getPid();

        out.println("Killing server");

        log.info("pid of the proccess is : " + pid);

        if (System.getProperty("os.name").contains("Windows") || System.getProperty("os.name").contains("windows"))  { // use taskkill
            Runtime.getRuntime().exec("taskkill /f /pid " + pid);
        } else { // on all other platforms use kill -9
            Runtime.getRuntime().exec("kill -9 " + pid);
        }
    }

    private long getPid()    {
        String jvmName = ManagementFactory.getRuntimeMXBean().getName();
        int index = jvmName.indexOf('@');

        if (index < 1) {
            // part before '@' empty (index = 0) / '@' not found (index = -1)
            throw new java.lang.IllegalStateException("Cannot get pid of the process:" + jvmName);
        }

        long pid = -1;

        try {
            pid = Long.parseLong(jvmName.substring(0, index));
        } catch (NumberFormatException e) {
            // ignore
        }
        return pid;
    }
}