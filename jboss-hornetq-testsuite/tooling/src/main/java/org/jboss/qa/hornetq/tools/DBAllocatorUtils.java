package org.jboss.qa.hornetq.tools;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.HttpRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Purpose of this class is to provide tooling for DB allocator.
 * 
 * @author mnovak@redhat.com
 */
public class DBAllocatorUtils {

    private static final Logger logger = Logger.getLogger(DBAllocatorUtils.class);

    /**
     *
     * @param database name of database @see HornetQTestCase
     * @return returns properties for allocated database db allocator
     * @throws Exception
     */
    public Map<String, String> allocateDatabase(String database) throws Exception {

        String response = "";
        String url = "http://dballocator.mw.lab.eng.bos.redhat.com:8080/Allocator/AllocatorServlet?operation=alloc&label="
                + database + "&expiry=60&requestee=eap6-hornetq-lodh5";
        logger.info("Allocate db: " + url);
        try {
            response = HttpRequest.get(url, 20, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Error during allocating Database.", e);
        }
        logger.info("Response is: " + response);
        // parse response
        Scanner lines = new Scanner(response);
        String line;
        Map<String, String> properties = new HashMap<String, String>();
        while (lines.hasNextLine()) {
            line = lines.nextLine();
            logger.info("Print line: " + line);
            if (!line.startsWith("#")) {
                String[] property = line.split("=");
                properties.put((property[0]), property[1].replaceAll("\\\\", ""));
                logger.info("Add property: " + property[0] + " " + property[1].replaceAll("\\\\", ""));
            }
        }
        return properties;
    }
}
