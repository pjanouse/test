package org.jboss.qa.tools;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Utitility to print journal content to a file.
 *
 * To print journal it's necessary to have hornetq-core.jar and netty.jar on classpath. It's not desirable to have
 * hornetq-core.jar dependency in pom.xml.
 * Workaround is to print journal in separate process which will take hornetq-core.jar to classpath.
 *
 * @author mnovak@redhat.com
 */
public class PrintJournal {

    public static void printJournal(String messagingbindingsDirectory, String messagingjournalDirectory, String outputFile)   {

    }

    public static void main(String args[]) {

        String command = "java -cp $JBOSS_HOME/modules/org/hornetq/main/*" +
                ":$JBOSS_HOME/modules/org/jboss/netty/main/* " +
                "org.hornetq.core.persistence.impl.journal.PrintData " +
                "$JBOSS_HOME/standalone/data/messagingbindings " +
                "$JBOSS_HOME/standalone/data/messagingjournal >  log_after_shutdown.txt";

        try {

            String line;
            Process p = Runtime.getRuntime().exec("cmd /c dir");
            BufferedReader bri = new BufferedReader
                    (new InputStreamReader(p.getInputStream()));
            BufferedReader bre = new BufferedReader
                    (new InputStreamReader(p.getErrorStream()));
            while ((line = bri.readLine()) != null) {
                System.out.println(line);
            }
            bri.close();
            while ((line = bre.readLine()) != null) {
                System.out.println(line);
            }
            bre.close();
            p.waitFor();
            System.out.println("Done.");
        }
        catch (Exception err) {
            err.printStackTrace();
        }
    }

}
