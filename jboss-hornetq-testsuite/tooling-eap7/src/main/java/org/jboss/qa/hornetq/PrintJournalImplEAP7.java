package org.jboss.qa.hornetq;

import org.apache.log4j.Logger;
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
public class PrintJournalImplEAP7 implements PrintJournal {

    private static final Logger log = Logger.getLogger(PrintJournalImplEAP7.class);

    private static String workingDirectory = System.getenv("WORKSPACE") == null ? new File(".").getAbsolutePath() : System.getenv("WORKSPACE");
    private static String jbossHome = System.getenv("JBOSS_HOME_1") != null ? System.getenv("JBOSS_HOME_1") : null;

    private Container container = null;

    private PrintJournalImplEAP7()  {}

    public PrintJournalImplEAP7(Container container)    {
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
                .append(File.separator).append("bindings");
        StringBuilder messagingJournalDirectoryBuilder = new StringBuilder(jbossHome);
        messagingJournalDirectoryBuilder.append(File.separator).append("standalone").append(File.separator).append("data")
                .append(File.separator).append("journal");
        StringBuilder pagingJournalDirectoryBuilder = new StringBuilder(jbossHome);
        pagingJournalDirectoryBuilder.append(File.separator).append("standalone").append(File.separator).append("data")
                .append(File.separator).append("paging");


        StringBuilder outputFileBuilder = new StringBuilder(workingDirectory);
        outputFileBuilder.append(File.separator).append(relativePathToOutputFile);

        printJournal(messagingJournalDirectoryBuilder.toString(), messagingJournalDirectoryBuilder.toString(), pagingJournalDirectoryBuilder.toString(), outputFileBuilder.toString());
    }

    /**
     * Prints journal to output file
     *  @param messagingbindingsDirectory
     * @param messagingjournalDirectory
     * @param messagingpagingDirectory
     * @param outputFile                 file to which content of journal will be printed
     */
    @Override
    public void printJournal(String messagingbindingsDirectory, String messagingjournalDirectory, String messagingpagingDirectory, String outputFile) throws Exception {

        if (workingDirectory == null || "".equalsIgnoreCase(workingDirectory)) {
            workingDirectory = new File(".").getAbsolutePath();
        }

        if (container == null) {
            throw new IllegalStateException("Container is null/empty.");
        }

        if (messagingbindingsDirectory == null || "".equalsIgnoreCase(messagingbindingsDirectory)) {
            throw new IllegalStateException("Parameter messagingbindingsDirectory is null/empty.");
        }

        if (messagingjournalDirectory == null || "".equalsIgnoreCase(messagingjournalDirectory)) {
            throw new IllegalStateException("Parameter messagingjournalDirectory is null/empty.");
        }

        if (messagingpagingDirectory == null || "".equals(messagingpagingDirectory)) {
            throw new IllegalStateException("Parameter messagingpagingDirectory is null/empty.");
        }

        if (outputFile == null || "".equalsIgnoreCase(outputFile)) {
            throw new IllegalStateException("Parameter outputFile is null/empty.");
        }

        File standaloneDir = new File(jbossHome, "standalone");
        File confXML = new File(standaloneDir, "configuration" + File.separator + "standalone-full-ha.xml");

        final JavaProcessBuilder javaProcessBuilder = new JavaProcessBuilder();

        javaProcessBuilder.setWorkingDirectory(workingDirectory);
        javaProcessBuilder.addClasspathEntry(buildClasspath(container));
        javaProcessBuilder.setMainClass("org.apache.activemq.artemis.cli.Artemis");
        javaProcessBuilder.addSystemProperty("artemis.instance", standaloneDir.getAbsolutePath());
        javaProcessBuilder.addArgument("data");
        javaProcessBuilder.addArgument("print");
        javaProcessBuilder.addArgument("--bindings");
        javaProcessBuilder.addArgument(messagingbindingsDirectory);
        javaProcessBuilder.addArgument("--journal");
        javaProcessBuilder.addArgument(messagingjournalDirectory);
        javaProcessBuilder.addArgument("--paging");
        javaProcessBuilder.addArgument(messagingpagingDirectory);
        javaProcessBuilder.addArgument("--configuration");
        javaProcessBuilder.addArgument(confXML.getAbsolutePath());

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
                ", journal directory: " + messagingjournalDirectory + ", paging directory: " + messagingpagingDirectory);

    }

    /**
     * Create classpath to one of the servers directory.
     *
     * @return classpat
     */
    private String buildClasspath(Container container) throws Exception {

        StringBuilder strbuilder = new StringBuilder();

        //modules/system/layers/base/org/hornetq/main/
        strbuilder.append(ServerPathUtils.getModuleDirectory(container, "org/apache/activemq/artemis")).append(File.separator).append("*");
        strbuilder.append(File.pathSeparator);
        strbuilder.append(ServerPathUtils.getModuleDirectory(container, "io/netty")).append(File.separator).append("*");
        strbuilder.append(File.pathSeparator);
        strbuilder.append(ServerPathUtils.getModuleDirectory(container, "org/jboss/logging")).append(File.separator).append("*");
        strbuilder.append(File.pathSeparator);
        strbuilder.append(ServerPathUtils.getModuleDirectory(container, "javax/jms/api")).append(File.separator).append("*");
        strbuilder.append(File.pathSeparator);
        strbuilder.append(System.getProperty("AIRLIFT_JAR"));
        strbuilder.append(File.pathSeparator);
        strbuilder.append(System.getProperty("GUAVA_JAR"));
        strbuilder.append(File.pathSeparator);
        strbuilder.append(System.getProperty("JAVAX_INJECT_JAR"));

        System.out.println("Classpath " + strbuilder);
        log.debug("Classpath: " + strbuilder);

        return strbuilder.toString();
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        PrintJournalImplEAP7.workingDirectory = workingDirectory;
    }

    public String getJbossHome() {
        return jbossHome;
    }

    public void setJbossHome(String jbossHome) {
        PrintJournalImplEAP7.jbossHome = jbossHome;
    }

    public static void main(String args[]) throws Exception {
        PrintJournalImplEAP7 pj = new PrintJournalImplEAP7();

        String messagingbindingsDirectory = "/home/mnovak/hornetq_eap6_dev/internal/7.0.0.DR1/server1/jboss-eap/standalone/data/activemq/bindings";
        String messagingjournalDirectory = "/home/mnovak/hornetq_eap6_dev/internal/7.0.0.DR1/server1/jboss-eap/standalone/data/activemq/journal";
        String outputFile = "/home/mnovak/hornetq_eap6_dev/internal/eap-tests-hornetq/jboss-hornetq-testsuite/journal_output.log";
        pj.setWorkingDirectory("/home/mnovak/hornetq_eap6_dev/internal/eap-tests-hornetq/jboss-hornetq-testsuite");
        pj.setJbossHome("/home/mnovak/hornetq_eap6_dev/internal/7.0.0.DR1/server1/jboss-eap");
        ContainerEAP7 containerEAP7 = new ContainerEAP7();
//        new PrintJournalImplEAP7().printJournal(, messagingbindingsDirectory, messagingbindingsDirectory, outputFile);
    }

}
