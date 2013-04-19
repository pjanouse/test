package org.jboss.qa.hornetq.test.integration;

import org.jboss.byteman.rule.Rule;
import org.jboss.byteman.rule.helper.Helper;

import javax.transaction.xa.XAResource;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Byteman Helper which put result of getXAResources to the test. Used in XARecoveryConfigTestCase.
 * <p/>
 * It stores the result to a file.
 */
public class XARecoveryConfigHelper extends Helper {

    static String jbossHome = System.getProperty("jboss.home.dir");

    protected XARecoveryConfigHelper(Rule rule) {
        super(rule);

        File result = new File(jbossHome + File.separator + "xa-resources.txt");

        if (result.exists()) {
            result.delete();
        }
    }

    /**
     * Set variable in test XARecoveryConfigTestCase
     *
     * @param xaResources XAResource with xaRecoverConfigs
     */
    public static void checkResult(XAResource[] xaResources) {

        StringBuilder resources = new StringBuilder();

        for (XAResource xaResource : xaResources) {
            System.out.println(xaResource);
            resources.append(xaResource.toString());
        }


        File result = new File(jbossHome + File.separator + "xa-resources.txt");

        if (result.exists()) {
            result.delete();
        }

        try {
            PrintWriter writer = new PrintWriter(result);
            writer.println(resources.toString());
            writer.close();
        } catch (FileNotFoundException e) {
            Logger.getLogger(XARecoveryConfigHelper.class.getName()).log(Level.SEVERE, e.getMessage());
        }
    }
}
