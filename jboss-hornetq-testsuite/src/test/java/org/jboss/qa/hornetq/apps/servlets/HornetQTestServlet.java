package org.jboss.qa.hornetq.apps.servlets;

import javax.jms.ConnectionFactory;
import javax.jms.XAConnectionFactory;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HornetQTestServlet extends HttpServlet {

    // Logger
    private static final Logger log = Logger.getLogger(HornetQTestServlet.class.getName());

    /**
     * @see {@link HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)}
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * @see {@link HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)}
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }

    /**
     * Process requests
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        PrintWriter out = response.getWriter();
        String op = request.getParameter("op");

        try {
            if (op != null) {
                if (op.equals("testConnectionFactoryType")) {

                    Context ctx = new InitialContext();

                    String jndiName = request.getParameter("jndiName");

                    log.info("testConnectionFactoryType was called on HornetQTestServlet with jndiName: " + jndiName);

                    if (jndiName == null || "".equals(jndiName))   {
                        out.println("jndiName is missing. Provide jndiName parameter to url.");
                    }

                    ConnectionFactory cf = (ConnectionFactory) ctx.lookup(jndiName);

                    if (cf == null) {
                        out.println("Connection factory lookup is null for jndiName: " + jndiName);
                    }

                    if (cf instanceof XAConnectionFactory)  {
                        out.println("true");
                    } else {
                        out.println("false");
                    }

                } else if (op.equals("ping")) {
                    out.println("pong");
                }
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, e.getMessage(), e);
            out.println("Problem occured when invoking operation: " + op + " message: " + e.getMessage());
            throw new IOException(e);
        } finally {

            out.close();
        }
    }
}