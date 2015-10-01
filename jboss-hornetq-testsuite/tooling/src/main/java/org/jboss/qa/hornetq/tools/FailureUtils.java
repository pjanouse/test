package org.jboss.qa.hornetq.tools;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HttpRequest;
import org.jboss.qa.hornetq.apps.servlets.OOMServlet;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;

import java.util.concurrent.TimeUnit;

/**
 * Creates deployments used in Container implementations.
 */
public class FailureUtils {

    private static final Logger log = Logger.getLogger(FailureUtils.class);

    private static Archive oomServlet = getOOMServlet();

    public static synchronized Archive getOOMServlet() {
        if (oomServlet == null) {
            oomServlet = createOOMServlet();
        }
        return oomServlet;
    }

    private static Archive createOOMServlet() {

        final WebArchive oomServlet = ShrinkWrap.create(WebArchive.class, "oomServlet.war");

        StringBuilder webXml = new StringBuilder();
        webXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> ");
        webXml.append("<web-app version=\"2.5\" xmlns=\"http://java.sun.com/xml/ns/javaee\" \n");
        webXml.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n");
        webXml.append("xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee \n");
        webXml.append("http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd\">\n");
        webXml.append("<servlet><servlet-name>OOMServlet</servlet-name>\n");
        webXml.append("<servlet-class>org.jboss.qa.hornetq.apps.servlets.OOMServlet</servlet-class></servlet>\n");
        webXml.append("<servlet-mapping><servlet-name>OOMServlet</servlet-name>\n");
        webXml.append("<url-pattern>/OOMServlet</url-pattern>\n");
        webXml.append("</servlet-mapping>\n");
        webXml.append("</web-app>\n");
        log.debug(webXml.toString());
        oomServlet.addAsWebInfResource(new StringAsset(webXml.toString()), "web.xml");
        StringBuilder jbossWebXml = new StringBuilder();
        jbossWebXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> \n");
        jbossWebXml.append("<jboss-web> \n");
        jbossWebXml.append("<context-root>/OOMServlet</context-root> \n");
        jbossWebXml.append("</jboss-web> \n");
        log.debug(jbossWebXml.toString());
        oomServlet.addAsWebInfResource(new StringAsset(jbossWebXml.toString()), "jboss-web.xml");
        oomServlet.addClasses(OOMServlet.class, Constants.class);
        log.info(oomServlet.toString(true));
        return oomServlet;
    }

    public void fail(Container container, Constants.FAILURE_TYPE failureType)    {
        if (Constants.FAILURE_TYPE.KILL.equals(failureType)) {
            container.kill();
        } else if (Constants.FAILURE_TYPE.SHUTDOWN.equals(failureType)) {
            container.stop();
        } else if (Constants.FAILURE_TYPE.OUT_OF_MEMORY_HEAP_SIZE.equals(failureType) ||
                Constants.FAILURE_TYPE.OUT_OF_MEMORY_UNABLE_TO_OPEN_NEW_NATIE_THREAD.equals(failureType)) {
            // deploy OOM servlet
            // cause OOM
            try {
                container.undeploy(FailureUtils.getOOMServlet());
            } catch (Exception ex) {
                // ignore
            }

            try {
                container.deploy(FailureUtils.getOOMServlet());
                String request = "http://" + container.getHostname() + ":" + container.getHttpPort() + "/OOMServlet/OOMServlet?op="
                        + failureType;
                log.info("Call OOM servlet: " + request);
                HttpRequest.get(request, 120, TimeUnit.SECONDS);

            } catch (Exception ex) {
                log.error("Print exception: ", ex);
            } finally {
                container.undeploy(FailureUtils.getOOMServlet());
            }
        } else {
            throw new RuntimeException("Failure type - " + failureType + " - NOT IMPLEMENTED.");
        }
    }
}
