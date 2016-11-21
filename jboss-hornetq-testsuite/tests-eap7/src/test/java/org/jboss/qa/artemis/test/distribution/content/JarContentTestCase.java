/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.qa.artemis.test.distribution.content;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.qa.hornetq.Container;
import org.junit.Test;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.junit.experimental.categories.Category;
import org.testng.Assert;

/**
 *
 * Test that artemis in EAP contains only expected jar files.
 *
 * @author mstyk
 */
@Category(FunctionalTests.class)
public class JarContentTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(JarContentTestCase.class);
    private static List<String> expectedJars = new ArrayList<String>();

    static {
        expectedJars.add("artemis-cli");
        expectedJars.add("artemis-dto");
        expectedJars.add("artemis-native");
        expectedJars.add("artemis-server");
        expectedJars.add("artemis-commons");
        expectedJars.add("artemis-journal");
        expectedJars.add("artemis-selector");
        expectedJars.add("artemis-jms-client");
        expectedJars.add("artemis-jms-server");
        expectedJars.add("artemis-core-client");
        expectedJars.add("artemis-hqclient-protocol");
        expectedJars.add("artemis-ra");
        expectedJars.add("artemis-service-extensions");
        expectedJars.add("artemis-hornetq-protocol");
        expectedJars.add("artemis-jdbc-store");
    }

    @Test
    public void checkContainingJars() {
        String pathToArtemisModule = getPathToArtemisModule(container(1));
        List<File> artemisJars = getJarFilesInDirectory(pathToArtemisModule);

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

    private String getPathToArtemisModule(Container container) {
        ContainerDef containerDef = container.getContainerDefinition();
        Map<String, String> containerProperties = containerDef.getContainerProperties();
        String jbossHome = containerProperties.get("jbossHome");
        StringBuilder pathToArtemisModuleBuilder = new StringBuilder(jbossHome)
                .append(File.separator)
                .append("modules")
                .append(File.separator)
                .append("system")
                .append(File.separator)
                .append("layers")
                .append(File.separator)
                .append("base")
                .append(File.separator)
                .append("org")
                .append(File.separator)
                .append("apache")
                .append(File.separator)
                .append("activemq")
                .append(File.separator)
                .append("artemis");

        return pathToArtemisModuleBuilder.toString();
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
