package org.jboss.qa.hornetq.tools;

import org.apache.commons.io.FileUtils;
import org.jboss.qa.hornetq.Container;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import sun.misc.IOUtils;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * Tool class for registering modules in EAP 7.
 */
public class ModuleUtils {

    private static String getModulePath(Container container, String moduleName) {
        Map<String, String> containerProperties = container.getContainerDefinition().getContainerProperties();
        String jbossHome = containerProperties.get("jbossHome");
        StringBuilder modulePath = new StringBuilder(jbossHome)
                .append(File.separator).append("modules")
                .append(File.separator).append("system")
                .append(File.separator).append("layers")
                .append(File.separator).append("base")
                .append(File.separator).append(moduleName.replace(".", File.separator))
                .append(File.separator).append("main");
        return modulePath.toString();
    }

    private static void createModuleDescriptor(File module, String moduleName, List<String> dependencies) throws ParserConfigurationException, TransformerException {
        final String namespace = "urn:jboss:module:1.3";

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = dbf.newDocumentBuilder();
        Document doc = builder.newDocument();
        Element moduleEl = doc.createElementNS(namespace, "module");
        moduleEl.setAttribute("name", moduleName);
        Element resourcesEl = doc.createElement("resources");

        Element resourceEl = doc.createElement("resource-root");
        resourceEl.setAttribute("path", "module.jar");
        resourcesEl.appendChild(resourceEl);

        Element dependenciesEl = doc.createElement("dependencies");
        for (String dependency : dependencies) {
            Element dependencyEl = doc.createElement("module");
            dependencyEl.setAttribute("name", dependency);
            dependenciesEl.appendChild(dependencyEl);
        }


        moduleEl.appendChild(resourcesEl);
        moduleEl.appendChild(dependenciesEl);
        doc.appendChild(moduleEl);

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(module, "module.xml"));
        transformer.transform(source, result);
    }

    /**
     *
     * @param container                         EAP 7 container
     * @param moduleName                        name of module, note: use test. prefix, these modules
     *                                          are automatically clean up before run the test
     * @param classes                           classes which will be inserted into the module
     * @param dependencies                      dependencies on other modules
     * @throws TransformerException
     * @throws ParserConfigurationException
     */
    public static void registerModule(Container container, String moduleName, List<Class> classes, List<String> dependencies) throws TransformerException, ParserConfigurationException {
        File moduleDir = new File(getModulePath(container, moduleName));
        moduleDir.mkdirs();

        JavaArchive moduleJar = ShrinkWrap.create(JavaArchive.class);
        for (Class clazz : classes) {
            moduleJar.addClass(clazz);
        }
        moduleJar.as(ZipExporter.class).exportTo(new File(moduleDir, "module.jar"));
        createModuleDescriptor(moduleDir, moduleName, dependencies);
    }

    public static void registerModule(Container container, String moduleName, URL pathToJar, List<String> dependencies) throws Exception {
        File moduleDir = new File(getModulePath(container, moduleName));
        moduleDir.mkdirs();
        FileUtils.copyURLToFile(pathToJar, new File(moduleDir, "module.jar"));
        createModuleDescriptor(moduleDir, moduleName, dependencies);

    }

}
