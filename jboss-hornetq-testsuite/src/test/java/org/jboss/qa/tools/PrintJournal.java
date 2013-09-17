package org.jboss.qa.tools;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.test.HornetQTestCase;

import java.io.*;

/**
 * Utitility to print journal content to a file.
 * <p/>
 * To print journal it's necessary to have hornetq-core.jar and netty.jar on classpath. It's not desirable to have
 * hornetq-core.jar dependency in pom.xml.
 * Workaround is to print journal in separate process which will take hornetq-core.jar to classpath.
 *
 * @author mnovak@redhat.com
 */
public class PrintJournal {

    private static final Logger log = Logger.getLogger(PrintJournal.class);

    private static String workingDirectory = System.getenv("WORKSPACE") != new File(".").getAbsolutePath() ? System.getenv("WORKSPACE") : null;
    private static String jbossHome = System.getenv("JBOSS_HOME_1") != null ? System.getenv("JBOSS_HOME_1") : null;

    /**
     * Prints journal to output file.
     *
     * @param container                container name
     * @param relativePathToOutputFile relative path against $WORKSPACE
     */
    public static void printJournal(String container, String relativePathToOutputFile) {

        StringBuilder messagingBindingsDirectoryBuilder = new StringBuilder(HornetQTestCase.getJbossHome(container));
        messagingBindingsDirectoryBuilder.append(File.separator).append("standalone").append(File.separator).append("data")
                .append(File.separator).append("messagingbindings");
        StringBuilder messagingJournalDirectoryBuilder = new StringBuilder(HornetQTestCase.getJbossHome(container));
        messagingJournalDirectoryBuilder.append(File.separator).append("standalone").append(File.separator).append("data")
                .append(File.separator).append("messagingjournal");

        if (workingDirectory == null || "".equalsIgnoreCase(workingDirectory)) {
            workingDirectory = new File(".").getAbsolutePath();
        }

        StringBuilder outputFileBuilder = new StringBuilder(workingDirectory);
        outputFileBuilder.append(File.separator).append(relativePathToOutputFile);
        printJournal("", messagingJournalDirectoryBuilder.toString(), messagingJournalDirectoryBuilder.toString(), outputFileBuilder.toString());
    }

    /**
     * Prints journal to output file
     *
     * @param messagingbindingsDirectory
     * @param messagingjournalDirectory
     * @param outputFile                 file to which content of journal will be printed
     */
    public static void printJournal(String jbossHome, String messagingbindingsDirectory, String messagingjournalDirectory, String outputFile) {

        if (workingDirectory == null || "".equalsIgnoreCase(workingDirectory)) {
            workingDirectory = new File(".").getAbsolutePath();
        }

        if (jbossHome == null || "".equalsIgnoreCase(jbossHome)) {
            throw new IllegalStateException("JBOSS_HOME is null/empty.");
        }

        if (messagingbindingsDirectory == null || "".equalsIgnoreCase(messagingbindingsDirectory)) {
            throw new IllegalStateException("Parameter messagingbindingsDirectory is null/empty.");
        }

        if (messagingjournalDirectory == null || "".equalsIgnoreCase(messagingjournalDirectory)) {
            throw new IllegalStateException("Parameter messagingjournalDirectory is null/empty.");
        }

        if (outputFile == null || "".equalsIgnoreCase(outputFile)) {
            throw new IllegalStateException("Parameter outputFile is null/empty.");
        }


        final JavaProcessBuilder javaProcessBuilder = new JavaProcessBuilder();

        javaProcessBuilder.setWorkingDirectory(workingDirectory);
        javaProcessBuilder.addClasspathEntry(buildClasspath());
        javaProcessBuilder.setMainClass("org.hornetq.core.persistence.impl.journal.PrintData");
        javaProcessBuilder.addArgument(messagingbindingsDirectory);
        javaProcessBuilder.addArgument(messagingjournalDirectory);

        final Process process;

        try {
            process = javaProcessBuilder.startProcess();

            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            File f = new File(outputFile);
            if (f.exists()) {
                f.delete();
            }
            f.createNewFile();
            BufferedWriter out = new BufferedWriter(new FileWriter(f));
            try {
                while ((line = br.readLine()) != null) {

                    log.debug(line);
                    out.write(line);
                    out.write("\n");
                }
            } finally {

                if (out != null) {
                    out.close();
                }
            }

        } catch (IOException e) {
            log.error("Cannot create or read output from process:", e);
        }

        log.info("Journal printed to file: " + outputFile + ", bindings directory: " + messagingbindingsDirectory +
                ", journal directory: " + messagingjournalDirectory);

    }

    /**
     * Create classpath to one of the servers directory.
     *
     * @return classpat
     */
    private static String buildClasspath() {

        StringBuilder strbuilder = new StringBuilder();
        if (jbossHome.contains("6.0")) {
            strbuilder.append(jbossHome).append(File.separator).append("modules").append(File.separator).append("org");
            strbuilder.append(File.separator).append("hornetq").append(File.separator).append("main").append(File.separator).append("*");
            strbuilder.append(File.pathSeparator);
            strbuilder.append(jbossHome).append(File.separator).append("modules").append(File.separator).append("org");
            strbuilder.append(File.separator).append("jboss").append(File.separator).append("netty").append(File.separator).append("main").append(File.separator).append("*");
        } else { //modules/system/layers/base/org/hornetq/main/
            strbuilder.append(jbossHome).append(File.separator).append("modules").append(File.separator).append("system").append(File.separator)
                    .append("layers").append(File.separator).append("base").append(File.separator).append("org")
                    .append(File.separator).append("hornetq").append(File.separator).append("main").append(File.separator).append("*");
            strbuilder.append(File.pathSeparator);
            strbuilder.append(jbossHome).append(File.separator).append("modules").append(File.separator).append("system").append(File.separator)
                    .append("layers").append(File.separator).append("base").append(File.separator).append("org")
                    .append(File.separator).append("jboss").append(File.separator).append("netty").append(File.separator).append("main").append(File.separator).append("*");
            strbuilder.append(File.pathSeparator);
            strbuilder.append(jbossHome).append(File.separator).append("modules").append(File.separator).append("system").append(File.separator)
                    .append("layers").append(File.separator).append("base").append(File.separator).append("org")
                    .append(File.separator).append("jboss").append(File.separator).append("logging").append(File.separator).append("main").append(File.separator).append("*");

        }

        log.debug("Classpath: " + strbuilder);

        return strbuilder.toString();
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        PrintJournal.workingDirectory = workingDirectory;
    }

    public String getJbossHome() {
        return jbossHome;
    }

    public void setJbossHome(String jbossHome) {
        PrintJournal.jbossHome = jbossHome;
    }

    public static void main(String args[]) {
        PrintJournal pj = new PrintJournal();

        String messagingbindingsDirectory = "/home/mnovak/tmp/jboss-eap-6.0/standalone/data/messagingbindings";
        String messagingjournalDirectory = "/home/mnovak/tmp/jboss-eap-6.0/standalone/data/messagingjournal";
        String outputFile = "/home/mnovak/tmp/hornetq_eap6_dev/internal/eap-tests-hornetq/jboss-hornetq-testsuite/journal_output.log";
        pj.setWorkingDirectory("/home/mnovak/tmp/hornetq_eap6_dev/internal/eap-tests-hornetq/jboss-hornetq-testsuite");
        pj.setJbossHome("/home/mnovak/tmp/jboss-eap-6.0");
        pj.printJournal("", messagingbindingsDirectory, messagingbindingsDirectory, outputFile);
    }

}
