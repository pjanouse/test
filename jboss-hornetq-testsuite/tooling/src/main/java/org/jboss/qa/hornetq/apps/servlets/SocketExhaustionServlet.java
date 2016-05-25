package org.jboss.qa.hornetq.apps.servlets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Servlet is used to exhaust all sockets.
 *
 * @author mstyk
 */
public class SocketExhaustionServlet extends HttpServlet {

    // Logger
    private static final Logger log = Logger.getLogger(SocketExhaustionServlet.class.getName());

    /**
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     * @see {@link HttpServlet#doGet(HttpServletRequest, HttpServletResponse)}
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        long duration = Long.parseLong(request.getParameter("duration"));
        String host = request.getParameter("host");
        int port = Integer.parseInt(request.getParameter("port"));

        causeSocketExhaustion(duration, host, port);
    }

    /**
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     * @see {@link HttpServlet#doPost(HttpServletRequest, HttpServletResponse)}
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }

    private void causeSocketExhaustion(long duration, String host, int port) throws IOException {

        log.info("Causing socket exhaustion.");

        List<Socket> sockets = new ArrayList<Socket>(32000);
        long start = System.currentTimeMillis();
        try {
            while (start + duration > System.currentTimeMillis()) {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 150);
                sockets.add(socket);
            }
        } finally {
            log.info("Opened " + sockets.size() + " sockets on " + host + ":" + port);
        }
    }

}
