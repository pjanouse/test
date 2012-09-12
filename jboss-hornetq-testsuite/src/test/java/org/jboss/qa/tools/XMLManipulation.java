package org.jboss.qa.tools;

import org.apache.log4j.Logger;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.util.Map;

/**
 * XMLManipulation
 * <p/>
 * Implements tools for the basic manipulations with the XML file
 */
public final class XMLManipulation {

    // Logger
    private static final Logger log = Logger.getLogger(XMLManipulation.class);

    /**
     * Returns DOM model of the given XML file
     *
     * @param inputFile source file
     * @return instance of the DOM model
     * @throws Exception if something goes wrong
     */
    public static Document getDOMModel(String inputFile) throws Exception {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        return documentBuilderFactory.newDocumentBuilder().parse(new InputSource(inputFile));
    }

    /**
     * Saves DOM model into the given file
     *
     * @param document   source DOM model
     * @param outputFile name of the output file
     * @throws TransformerException if something goes wrong
     */
    protected static void saveDOMModel(Document document, String outputFile) throws TransformerException {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(new DOMSource(document), new StreamResult(new File(outputFile)));
    }

    /**
     * Sets content of the node defined by xpath, replaces all occurrences
     *
     * @param xpath location of the content defined by xpath
     * @param value new value
     * @param doc   instance of the DOM model
     * @throws Exception if something goes wrong
     */
    public static void setNodeContent(String xpath, String value, Document doc) throws Exception {
        XPath xpathInstance = XPathFactory.newInstance().newXPath();
        NodeList nodes = (NodeList) xpathInstance.evaluate(xpath, doc, XPathConstants.NODESET);

        for (int idx = 0; idx < nodes.getLength(); idx++) {
            nodes.item(idx).setTextContent(value);
        }

    }

    /**
     * Returns content of the node defined by xpath, returns the first occurrence
     *
     * @param xpath location of the content defined by xpath
     * @param doc   instance of the DOM model
     * @return content of the node
     * @throws Exception if something goes wrong
     */
    public static String getNodeContent(String xpath, Document doc) throws Exception {
        String content;
        XPath xpathInstance = XPathFactory.newInstance().newXPath();
        Node node = (Node) xpathInstance.evaluate(xpath, doc, XPathConstants.NODE);
        content = (node != null) ? node.getTextContent() : null;
        return content;
    }

    /**
     * Sets parameter in XML element defined by xpath, replaces all occurrences
     *
     * @param xpath location of the parameter defined by xpath
     * @param value new value for the content
     * @param doc   instance of the DOM model
     * @throws Exception if something goes wrong
     */
    public static void setParameter(String xpath, String value, Document doc) throws Exception {
        XPath xpathInstance = XPathFactory.newInstance().newXPath();
        NodeList nodes = (NodeList) xpathInstance.evaluate(xpath, doc, XPathConstants.NODESET);
        for (int idx = 0; idx < nodes.getLength(); idx++) {
            Attr attr = (Attr) nodes.item(idx);
            attr.setValue(value);
        }
    }

    /**
     * Returns content of the parameter from XML file defined by xpath
     *
     * @param xpath location of the parameter defined by xpath
     * @param doc   instance of the DOM model
     * @return content of the parameter
     * @throws Exception if something goes wrong
     */
    public static String getParameter(String xpath, Document doc) throws Exception {
        String content = null;
        XPath xpathInstance = XPathFactory.newInstance().newXPath();
        NodeList node = (NodeList) xpathInstance.evaluate(xpath, doc, XPathConstants.NODESET);
        if (node.getLength() > 0) {
            content = ((Attr) node.item(0)).getValue();
        }
        return content;
    }

    /**
     * Adds new XML node into the DOM model, location is defined by xpath
     *
     * @param xpath location of the parameter defined by xpath
     * @param name  name of the new xml element
     * @param value content of the xml element
     * @param doc   instance of the DOM model
     * @throws Exception if something goes wrong
     */
    public static void addNode(String xpath, String name, String value, Document doc) throws Exception {
        addNode(xpath, name, value, doc, null);
    }

    /**
     * Adds new XML node into the DOM model, location is defined by xpath
     *
     * @param xpath      location of the parameter defined by xpath
     * @param name       name of the new xml element
     * @param value      content of the xml element
     * @param doc        instance of the DOM model
     * @param attributes map: attributeName -> attributeValue for the element
     * @throws Exception if something goes wrong
     */
    public static void addNode(String xpath, String name, String value, Document doc, Map<String, String> attributes) throws Exception {
        XPath xpathInstance = XPathFactory.newInstance().newXPath();
        Node node = (Node) xpathInstance.evaluate(xpath, doc, XPathConstants.NODE);
        if (node != null) {
            Element e = doc.createElement(name);
            if (attributes != null && attributes.size() > 0) {
                for (String key : attributes.keySet()) {
                    e.setAttribute(key, attributes.get(key));
                }
            }
            e.appendChild(doc.createTextNode(value));
            node.appendChild(e);
        } else {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Cannot find xpath '%s'", xpath));
            }
        }
    }

    /**
     * Removes XML node from the DOM model, location is defined by xpath
     *
     * @param xpath location of the parameter defined by xpath
     * @param doc   instance of the DOM model
     * @throws Exception if something goes wrong
     */
    public static void removeNode(String xpath, Document doc) throws Exception {
        XPath xpathInstance = XPathFactory.newInstance().newXPath();
        Node node = (Node) xpathInstance.evaluate(xpath, doc, XPathConstants.NODE);
        if (node != null) {
            node.getParentNode().removeChild(node);
        } else {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Cannot find xpath '%s'", xpath));
            }
        }
    }
}
