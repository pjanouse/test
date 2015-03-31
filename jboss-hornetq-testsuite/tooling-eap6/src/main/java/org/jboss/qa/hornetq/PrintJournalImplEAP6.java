package org.jboss.qa.hornetq;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.tools.ContainerInfo;
import org.jboss.qa.hornetq.tools.JavaProcessBuilder;
import org.jboss.qa.hornetq.tools.ServerPathUtils;

import java.io.*;

/**
 * Utility to print journal content to a file.
 * <p/>
 * To print journal it's necessary to have hornetq-core.jar and netty.jar on classpath. It's not desirable to have
 * hornetq-core.jar dependency in pom.xml.
 * Workaround is to print journal in separate process which will take hornetq-core.jar to classpath.
 *
 * @author mnovak@redhat.com
 */
public class PrintJournalImplEAP6 implements PrintJournal {

    private static final Logger log = Logger.getLogger(PrintJournalImplEAP6.class);

    private static String workingDirectory = System.getenv("WORKSPACE") == null ? new File(".").getAbsolutePath() : System.getenv("WORKSPACE");
    private static String jbossHome = System.getenv("JBOSS_HOME_1") != null ? System.getenv("JBOSS_HOME_1") : null;

    Container container = null;

    private  PrintJournalImplEAP6() {}

    public PrintJournalImplEAP6(Container container)    {
        this.container = container;
    }

    /**
     * Prints journal to output file.
     *
     * @param relativePathToOutputFile relative path against $WORKSPACE
     */
    @Override
    public void printJournal(String relativePathToOutputFile) throws Exception {

        jbossHome = container.getServerHome();

        StringBuilder messagingBindingsDirectoryBuilder = new StringBuilder(jbossHome);
        messagingBindingsDirectoryBuilder.append(File.separator).append("standalone").append(File.separator).append("data")
                .append(File.separator).append("messagingbindings");
        StringBuilder messagingJournalDirectoryBuilder = new StringBuilder(jbossHome);
        messagingJournalDirectoryBuilder.append(File.separator).append("standalone").append(File.separator).append("data")
                .append(File.separator).append("messagingjournal");


        StringBuilder outputFileBuilder = new StringBuilder(workingDirectory);
        outputFileBuilder.append(File.separator).append(relativePathToOutputFile);

        printJournal(messagingJournalDirectoryBuilder.toString(), messagingJournalDirectoryBuilder.toString(), outputFileBuilder.toString());
    }

    /**
     * Prints journal to output file
     *
     * @param messagingbindingsDirectory
     * @param messagingjournalDirectory
     * @param outputFile                 file to which content of journal will be printed
     */
    @Override
    public void printJournal(String messagingbindingsDirectory, String messagingjournalDirectory, String outputFile) throws Exception {

        if (workingDirectory == null || "".equalsIgnoreCase(workingDirectory)) {
            workingDirectory = new File(".").getAbsolutePath();
        }

        if (container == null) {
            throw new IllegalStateException("Container is null.");
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
        javaProcessBuilder.addClasspathEntry(buildClasspath(container));
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
    private static String buildClasspath(Container container) throws Exception {

        StringBuilder strbuilder = new StringBuilder();

        //modules/system/layers/base/org/hornetq/main/
        strbuilder.append(ServerPathUtils.getModuleDirectory(container, "org/hornetq")).append(File.separator).append("*");
        strbuilder.append(File.pathSeparator);
        strbuilder.append(ServerPathUtils.getModuleDirectory(container, "org/jboss/netty")).append(File.separator).append("*");
        strbuilder.append(File.pathSeparator);
        strbuilder.append(ServerPathUtils.getModuleDirectory(container, "org/jboss/logging")).append(File.separator).append("*");




        System.out.println("Classpath " + strbuilder);
        log.debug("Classpath: " + strbuilder);

        return strbuilder.toString();
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        PrintJournalImplEAP6.workingDirectory = workingDirectory;
    }

    public String getJbossHome() {
        return jbossHome;
    }

    public void setJbossHome(String jbossHome) {
        PrintJournalImplEAP6.jbossHome = jbossHome;
    }

    public static void main(String args[]) throws Exception {
        PrintJournalImplEAP6 pj = new PrintJournalImplEAP6();

        String messagingbindingsDirectory = "/home/mnovak/hornetq_eap6_dev/internal/6.3.1.CP.CR1/server1/jboss-eap-6.3/standalone/data/messagingbindings";
        String messagingjournalDirectory = "/home/mnovak/hornetq_eap6_dev/internal/6.3.1.CP.CR1/server1/jboss-eap-6.3/standalone/data/messagingjournal";
        String outputFile = "/home/mnovak/hornetq_eap6_dev/internal/eap-tests-hornetq/jboss-hornetq-testsuite/journal_output.log";
        pj.setWorkingDirectory("/home/mnovak/hornetq_eap6_dev/internal/eap-tests-hornetq/jboss-hornetq-testsuite");
        pj.setJbossHome("/home/mnovak/hornetq_eap6_dev/internal/6.3.1.CP.CR1/server1/jboss-eap-6.3");
//        new PrintJournalImplEAP6().printJournal("/home/mnovak/hornetq_eap6_dev/internal/6.3.1.CP.CR1/server1/jboss-eap-6.3", messagingbindingsDirectory, messagingbindingsDirectory, outputFile);
    }

}
