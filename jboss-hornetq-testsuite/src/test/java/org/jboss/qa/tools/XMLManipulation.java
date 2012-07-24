package org.jboss.qa.tools;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
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

/**
 * XMLManipulation
 * <p/>
 * Implements tools for the basic manipulation with the XML files
 */
public final class XMLManipulation {

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
}
