package org.jboss.qa.tools.byteman.rule;


import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for helping with method calls tracking inside HornetQ server.
 *
 * @author <a href="mailto:msvehla@redhat.com">Martin Svehla</a>
 */
public final class HornetQCallsTracking {

    public static final String CLIENT_SESSION_XA_RESOURCE_RULES = "byteman/client-session.btm";

    public static final String HORNETQ_CORE_TRANSACTION_RULES = "byteman/hornetq-core-transaction.btm";

    public static final String HORNETQ_RA_XA_RESOURCE_RULES = "byteman/hornetq-ra-xa-resource.btm";

    public static final String HORNETQ_XA_RESOURCE_WRAPPER_RULES = "byteman/hornetq-xa-resource-wrapper.btm";

    public static final String JOURNAL_RULES = "byteman/journal.btm";

    public static final String STORE_MANAGER_RULES = "byteman/journal-storage-manager.btm";

    public static final String TRANSACTION_MANAGER_RULES = "byteman/transaction-manager.btm";

    // This ruleset is not active by default, beacause it generates errors with JDBC XA datasources
    public static final String XA_RESOURCE_RULES = "byteman/xa-resource.btm";

    private static final String[] ALL_RULE_FILES = new String[]{
        CLIENT_SESSION_XA_RESOURCE_RULES,
        HORNETQ_CORE_TRANSACTION_RULES,
        HORNETQ_RA_XA_RESOURCE_RULES,
        HORNETQ_XA_RESOURCE_WRAPPER_RULES,
        JOURNAL_RULES,
        STORE_MANAGER_RULES,
        TRANSACTION_MANAGER_RULES
    };


    private HornetQCallsTracking() {
    }


    public static void installTrackingRules(final String host, final int port) {
        installTrackingRules(host, port, ALL_RULE_FILES);
    }


    public static void installTrackingRules(final String host, final int port, final String... rulesFiles) {
        SubmitUtil.host = host;
        SubmitUtil.port = port;

        SubmitUtil.installFromFiles(rulesFiles);
    }

}
