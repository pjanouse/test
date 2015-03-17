package org.jboss.qa.hornetq;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JavaProcessBuilder;
import org.jboss.qa.hornetq.tools.ServerPathUtils;

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

    private static String workingDirectory = System.getenv("WORKSPACE") == null ? new File(".").getAbsolutePath() : System.getenv("WORKSPACE");
    private static String jbossHome = System.getenv("JBOSS_HOME_1") != null ? System.getenv("JBOSS_HOME_1") : null;

    /**
     * Prints journal to output file.
     *
     * @param container                container name
     * @param relativePathToOutputFile relative path against $WORKSPACE
     */
    public static void printJournal(String container, String relativePathToOutputFile) throws Exception {

        jbossHome = ContainerUtils.getInstance().getJbossHome(container);

        StringBuilder messagingBindingsDirectoryBuilder = new StringBuilder(jbossHome);
        messagingBindingsDirectoryBuilder.append(File.separator).append("standalone").append(File.separator).append("data")
                .append(File.separator).append("messagingbindings");
        StringBuilder messagingJournalDirectoryBuilder = new StringBuilder(jbossHome);
        messagingJournalDirectoryBuilder.append(File.separator).append("standalone").append(File.separator).append("data")
                .append(File.separator).append("messagingjournal");


        StringBuilder outputFileBuilder = new StringBuilder(workingDirectory);
        outputFileBuilder.append(File.separator).append(relativePathToOutputFile);

        printJournal(jbossHome, messagingJournalDirectoryBuilder.toString(), messagingJournalDirectoryBuilder.toString(), outputFileBuilder.toString());
    }

    /**
     * Prints journal to output file
     *
     * @param messagingbindingsDirectory
     * @param messagingjournalDirectory
     * @param outputFile                 file to which content of journal will be printed
     */
    public static void printJournal(String jbossHome, String messagingbindingsDirectory, String messagingjournalDirectory, String outputFile) throws Exception {

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

                out.close();
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
    private static String buildClasspath() throws Exception {

        StringBuilder strbuilder = new StringBuilder();

        //modules/system/layers/base/org/hornetq/main/
        strbuilder.append(ServerPathUtils.getModuleDirectory(jbossHome, "org/hornetq")).append(File.separator).append("*");
        strbuilder.append(File.pathSeparator);
        strbuilder.append(ServerPathUtils.getModuleDirectory(jbossHome, "org/jboss/netty")).append(File.separator).append("*");
        strbuilder.append(File.pathSeparator);
        strbuilder.append(ServerPathUtils.getModuleDirectory(jbossHome, "org/jboss/logging")).append(File.separator).append("*");




        System.out.println("Classpath " + strbuilder);
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

    public static void main(String args[]) throws Exception {
        PrintJournal pj = new PrintJournal();

        String messagingbindingsDirectory = "/home/mnovak/hornetq_eap6_dev/internal/6.3.1.CP.CR1/server1/jboss-eap-6.3/standalone/data/messagingbindings";
        String messagingjournalDirectory = "/home/mnovak/hornetq_eap6_dev/internal/6.3.1.CP.CR1/server1/jboss-eap-6.3/standalone/data/messagingjournal";
        String outputFile = "/home/mnovak/hornetq_eap6_dev/internal/eap-tests-hornetq/jboss-hornetq-testsuite/journal_output.log";
        pj.setWorkingDirectory("/home/mnovak/hornetq_eap6_dev/internal/eap-tests-hornetq/jboss-hornetq-testsuite");
        pj.setJbossHome("/home/mnovak/hornetq_eap6_dev/internal/6.3.1.CP.CR1/server1/jboss-eap-6.3");
        printJournal("/home/mnovak/hornetq_eap6_dev/internal/6.3.1.CP.CR1/server1/jboss-eap-6.3", messagingbindingsDirectory, messagingbindingsDirectory, outputFile);
    }

}
