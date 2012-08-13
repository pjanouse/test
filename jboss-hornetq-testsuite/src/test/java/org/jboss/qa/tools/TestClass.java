package org.jboss.qa.tools;

import org.w3c.dom.Document;

/**
 * Created with IntelliJ IDEA.
 * User: pslavice
 * Date: 7/24/12
 * Time: 4:36 PM
 * To change this template use File | Settings | File Templates.
 */
public class TestClass {

    public static void main(String[] params) {
        try {
            Document doc = XMLManipulation.getDOMModel("/home/pslavice/tmp/hornetq-configuration.xml");
            System.out.println(XMLManipulation.getNodeContent("//redistribution-delay", doc));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
