/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.qa.artemis.test.distribution.content;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.tools.ServerPathUtils;
import org.junit.Test;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.junit.experimental.categories.Category;
import org.testng.Assert;

/**
 * Test that artemis in EAP contains only expected jar files.
 *
 * @author mstyk
 */
@Category(FunctionalTests.class)
public class JarContentTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(JarContentTestCase.class);
    private static List<String> expectedMainModuleJars = new ArrayList<String>();
    private static List<String> expectedJournalModuleJars = new ArrayList<String>();
    private static List<String> expectedRAModuleJars = new ArrayList<String>();
    private static List<String> expectedHornetQProtocolModuleJars = new ArrayList<String>();

    static {
        expectedMainModuleJars.add("artemis-cli");
        expectedMainModuleJars.add("artemis-dto");
        expectedMainModuleJars.add("artemis-server");
        expectedMainModuleJars.add("artemis-selector");
        expectedMainModuleJars.add("artemis-jms-client");
        expectedMainModuleJars.add("artemis-jms-server");
        expectedMainModuleJars.add("artemis-core-client");
        expectedMainModuleJars.add("artemis-jdbc-store");
        expectedMainModuleJars.add("artemis-hqclient-protocol");
        expectedMainModuleJars.add("artemis-jdbc-store");

        expectedJournalModuleJars.add("artemis-journal");
        expectedJournalModuleJars.add("artemis-native");
        expectedJournalModuleJars.add("artemis-commons");
        
        expectedRAModuleJars.add("artemis-ra");
        expectedRAModuleJars.add("artemis-service-extensions");

        expectedHornetQProtocolModuleJars.add("artemis-hornetq-protocol");
    }

    @Test
    public void checkArtemisMainModuleJars() throws Exception {
        String pathToArtemisModule = ServerPathUtils.getModuleDirectory(container(1), "org/apache/activemq/artemis").getAbsolutePath();
        checkModuleJars(pathToArtemisModule, expectedMainModuleJars);
    }

    @Test
    public void checkArtemisRAModuleJars() throws Exception {
        String pathToArtemisModule = ServerPathUtils.getModuleDirectory(container(1), "org/apache/activemq/artemis/ra").getAbsolutePath();
        checkModuleJars(pathToArtemisModule, expectedRAModuleJars);
    }

    @Test
    public void checkArtemisJournalModuleJars() throws Exception {
        String pathToArtemisModule = ServerPathUtils.getModuleDirectory(container(1), "org/apache/activemq/artemis/journal").getAbsolutePath();
        checkModuleJars(pathToArtemisModule, expectedJournalModuleJars);
    }

    @Test
    public void checkArtemisProtocolModuleJars() throws Exception {
        String pathToArtemisModule = ServerPathUtils.getModuleDirectory(container(1), "org/apache/activemq/artemis/protocol/hornetq").getAbsolutePath();
        checkModuleJars(pathToArtemisModule, expectedHornetQProtocolModuleJars);
    }

    private void checkModuleJars(String pathToModule, List<String> expectedJars) {

        List<File> artemisJars = getJarFilesInDirectory(pathToModule);
        List<File> unexpectedFile = new ArrayList<File>();

        //check there is nothing more in distribution than expected
        for (int i = 0; i < artemisJars.size(); i++) {
            File inDistributionFile = artemisJars.get(i);
            String inDistributionFileName = inDistributionFile.getName();

            boolean found = false;
            for (String s : expectedJars) {
                if (inDistributionFileName.startsWith(s)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                unexpectedFile.add(inDistributionFile);
            }
        }

        List<String> missingFile = new ArrayList<String>();

        //check there is everything expected in distribution
        for (int i = 0; i < expectedJars.size(); i++) {
            String expectedFileNameStart = expectedJars.get(i);

            boolean found = false;
            for (File f : artemisJars) {
                if (f.getName().startsWith(expectedFileNameStart)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                missingFile.add(expectedFileNameStart);
            }
        }

        int numProblems = +missingFile.size() + unexpectedFile.size();
        log.info("Number of problems with jars in artemis module" + numProblems);

        for (File f : unexpectedFile) {
            log.error("Unexpected jar file " + f.getAbsolutePath());
        }
        for (String s : missingFile) {
            log.error("Missing jar file " + s);
        }

        Assert.assertTrue(missingFile.isEmpty(), "Expected jar files are missing in artemis module. Problems in " + missingFile.toString());
        Assert.assertTrue(unexpectedFile.isEmpty(), "Unexpected jar files found in artemis module. Problems in " + unexpectedFile.toString());
        Assert.assertTrue(expectedJars.size() == artemisJars.size(), "Number of artemis jars in artemis module is unexpected");

    }

    private List<File> getJarFilesInDirectory(String directoryPath) {

        List<File> files = new ArrayList<File>();

        File directory = new File(directoryPath);

        File[] fList = directory.listFiles();
        for (File file : fList) {
            if (file.isFile()) {
                if (file.getName().endsWith(".jar")) {
                    files.add(file);
                }
            } else if (file.isDirectory()) {
                files.addAll(getJarFilesInDirectory(file.getAbsolutePath()));
            }
        }
        return files;
    }

}
