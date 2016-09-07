package org.jboss.qa.hornetq.apps.servlets;

import org.jboss.logging.Logger;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.jms.JMSContext;
import javax.jms.Queue;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicInteger;

@WebServlet("/producer")
public class ServletProducerInjectedJMSContext extends HttpServlet {

    private static final Logger logger = Logger.getLogger(ServletProducerInjectedJMSContext.class);

    private static final AtomicInteger counter = new AtomicInteger(0);

    @Inject
    private JMSContext jmsContext;

    @Resource(lookup = "java:/jms/queue/InQueue")
    private Queue queue;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        try {
            int i = counter.getAndIncrement();
            if (jmsContext == null) {
                logger.error("JMSContext is null");
            }
            jmsContext.createProducer().send(queue, jmsContext.createTextMessage("Message" + i));
            logger.info("Message" + i + " was sent");
            resp.setContentType("text/html");
            PrintWriter out = resp.getWriter();
            out.println("<html><body><h1>OK</h1></body></html>");
            out.flush();
        } catch (Exception e) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);
            e.printStackTrace(writer);
            resp.sendError(500, stringWriter.toString());
        }
    }
}
