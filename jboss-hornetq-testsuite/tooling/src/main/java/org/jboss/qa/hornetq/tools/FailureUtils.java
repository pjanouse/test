package org.jboss.qa.hornetq.tools;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HttpRequest;
import org.jboss.qa.hornetq.apps.servlets.GCPauseServlet;
import org.jboss.qa.hornetq.apps.servlets.OOMServlet;
import org.jboss.qa.hornetq.apps.servlets.SocketExhaustionServlet;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Creates deployments used in Container implementations.
 */
public class FailureUtils {

    private static final Logger log = Logger.getLogger(FailureUtils.class);

    private static Archive oomServlet = getOOMServlet();
    private static Archive gcPauseServlet = getGcPauseServlet();
    private static Archive socketExhaustionServlet = getSocketExhaustionServlet();

    public static synchronized Archive getOOMServlet() {
        if (oomServlet == null) {
            oomServlet = createOOMServlet();
        }
        return oomServlet;
    }

    public static synchronized Archive getGcPauseServlet() {
        if (gcPauseServlet == null) {
            gcPauseServlet = createGCPauseServlet();
        }
        File target = new File("/tmp/gcPauseServlet.war");
        if (target.exists()) {
            target.delete();
        }
        gcPauseServlet.as(ZipExporter.class).exportTo(target, true);
        return gcPauseServlet;
    }

    public static synchronized Archive getSocketExhaustionServlet() {
        if (socketExhaustionServlet == null) {
            socketExhaustionServlet = createSocketExhaustionServlet();
        }
        return socketExhaustionServlet;
    }

    private static Archive createGCPauseServlet() {

        final WebArchive gcPauseServlet = ShrinkWrap.create(WebArchive.class, "gcPauseServlet.war");

        StringBuilder webXml = new StringBuilder();
        webXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> ");
        webXml.append("<web-app version=\"2.5\" xmlns=\"http://java.sun.com/xml/ns/javaee\" \n");
        webXml.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n");
        webXml.append("xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee \n");
        webXml.append("http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd\">\n");
        webXml.append("<servlet><servlet-name>GCPauseServlet</servlet-name>\n");
        webXml.append("<servlet-class>org.jboss.qa.hornetq.apps.servlets.GCPauseServlet</servlet-class></servlet>\n");
        webXml.append("<servlet-mapping><servlet-name>GCPauseServlet</servlet-name>\n");
        webXml.append("<url-pattern>/GCPauseServlet</url-pattern>\n");
        webXml.append("</servlet-mapping>\n");
        webXml.append("</web-app>\n");
        log.debug(webXml.toString());
        gcPauseServlet.addAsWebInfResource(new StringAsset(webXml.toString()), "web.xml");
        StringBuilder jbossWebXml = new StringBuilder();
        jbossWebXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> \n");
        jbossWebXml.append("<jboss-web> \n");
        jbossWebXml.append("<context-root>/GCPauseServlet</context-root> \n");
        jbossWebXml.append("</jboss-web> \n");
        log.debug(jbossWebXml.toString());
        gcPauseServlet.addAsWebInfResource(new StringAsset(jbossWebXml.toString()), "jboss-web.xml");
        gcPauseServlet.addClasses(GCPauseServlet.class, Constants.class);
        log.info(gcPauseServlet.toString(true));
        return gcPauseServlet;
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

    private static Archive createSocketExhaustionServlet() {

        final WebArchive socketServlet = ShrinkWrap.create(WebArchive.class, "socketServlet.war");

        StringBuilder webXml = new StringBuilder();
        webXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> ");
        webXml.append("<web-app version=\"2.5\" xmlns=\"http://java.sun.com/xml/ns/javaee\" \n");
        webXml.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n");
        webXml.append("xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee \n");
        webXml.append("http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd\">\n");
        webXml.append("<servlet><servlet-name>SocketExhaustionServlet</servlet-name>\n");
        webXml.append("<servlet-class>org.jboss.qa.hornetq.apps.servlets.SocketExhaustionServlet</servlet-class></servlet>\n");
        webXml.append("<servlet-mapping><servlet-name>SocketExhaustionServlet</servlet-name>\n");
        webXml.append("<url-pattern>/SocketExhaustionServlet</url-pattern>\n");
        webXml.append("</servlet-mapping>\n");
        webXml.append("</web-app>\n");
        log.debug(webXml.toString());
        socketServlet.addAsWebInfResource(new StringAsset(webXml.toString()), "web.xml");
        StringBuilder jbossWebXml = new StringBuilder();
        jbossWebXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> \n");
        jbossWebXml.append("<jboss-web> \n");
        jbossWebXml.append("<context-root>/SocketExhaustionServlet</context-root> \n");
        jbossWebXml.append("</jboss-web> \n");
        log.debug(jbossWebXml.toString());
        socketServlet.addAsWebInfResource(new StringAsset(jbossWebXml.toString()), "jboss-web.xml");
        socketServlet.addClasses(SocketExhaustionServlet.class, Constants.class);
        log.info(socketServlet.toString(true));
        return socketServlet;
    }


    public void fail(Container container, Constants.FAILURE_TYPE failureType) {
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
            }
        } else if (Constants.FAILURE_TYPE.CPU_OVERLOAD.equals(failureType)) {
            // bind mdb EAP server to cpu core
            String cpuToBind = "0";
            try {
                HighCPUUtils.causeMaximumCPULoadOnContainer(container, cpuToBind);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            log.info("High Cpu loader was bound to cpu: " + cpuToBind);
        } else if (Constants.FAILURE_TYPE.GC_PAUSE.equals(failureType)) {
            // deploy gc pause servlet
            // cause gc pause servlet
            try {
                container.undeploy(FailureUtils.getGcPauseServlet());
            } catch (Exception ex) {
                // ignore
            }

            try {
                container.deploy(FailureUtils.getGcPauseServlet());
                String request = "http://" + container.getHostname() + ":" + container.getHttpPort() + "/GCPauseServlet/GCPauseServlet?op="
                        + failureType + "&duration=120000";
                log.info("Call GCPauseServlet servlet: " + request);
                HttpRequest.get(request, 130, TimeUnit.SECONDS);

            } catch (Exception ex) {
                log.error("Print exception: ", ex);
            } finally {
                container.undeploy(FailureUtils.getGcPauseServlet());
            }
        } else if (Constants.FAILURE_TYPE.SOCKET_EXHAUSTION.equals(failureType)) {
            try {
                container.deploy(socketExhaustionServlet);
                String request = "http://" + container.getHostname() + ":" + container.getHttpPort() + "/SocketExhaustionServlet/SocketExhaustionServlet?duration=30000&host="
                        + container.getHostname() + "&port=" + container.getHttpPort();
                log.info("Call SocketExhaustionServlet servlet: " + request);
                HttpRequest.get(request, 40, TimeUnit.SECONDS);
            } catch (Exception ex) {
                log.error("Print exception: ", ex);
            } finally {
                container.undeploy(socketExhaustionServlet);
            }
        } else {
            throw new RuntimeException("Failure type - " + failureType + " - NOT IMPLEMENTED.");
        }
    }
}
