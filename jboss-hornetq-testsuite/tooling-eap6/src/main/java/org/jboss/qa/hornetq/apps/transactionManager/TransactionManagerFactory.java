package org.jboss.qa.hornetq.apps.transactionManager;

import com.arjuna.ats.arjuna.common.arjPropertyManager;
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionManagerImple;
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple;
import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;

import javax.transaction.TransactionManager;

/**
* This class create transaction manager which can be used in xa org.jboss.qa.hornetq.apps.clients.
*
* @author mnovak@redhat.com
*/
public class TransactionManagerFactory {

    public static javax.transaction.TransactionManager txMgr = null;
    public static Object txMgrLock = new Object();

    public static String nodeIdentifierAndObjectStoreDir = "ObjectStore";

    private TransactionManagerFactory()   {

    }

    public static TransactionManager getInstance(String nodeIdentifier, String objectStoreDir) throws Exception {
        if (txMgr == null)  {
            //create transaction manager
            txMgr = createTransactionManager(nodeIdentifier, objectStoreDir);
        }
        return txMgr;
    }

    private static TransactionManager createTransactionManager(String nodeIdentifier, String objectStoreDir) throws Exception {
        System.setProperty(JTAEnvironmentBean.class.getSimpleName() + "." + "transactionManagerClassName", TransactionManagerImple.class.getName());
        System.setProperty(JTAEnvironmentBean.class.getSimpleName() + "." + "transactionSynchronizationRegistryClassName", TransactionSynchronizationRegistryImple.class.getName());
        BeanPopulator.getDefaultInstance(JTAEnvironmentBean.class).setXaAssumeRecoveryComplete(false);
        // this is necessary - because without prepare all this is bad idea
        // TODO this is a bad idea to force two phase commmit, but without it client does not have deterministic way, what to do with messages from failed transaction when failover occurs
        arjPropertyManager.getCoordinatorEnvironmentBean().setCommitOnePhase(false);

        arjPropertyManager.getCoreEnvironmentBean().setNodeIdentifier(nodeIdentifier);
        arjPropertyManager.getObjectStoreEnvironmentBean().setObjectStoreDir(objectStoreDir);
        // it might happen that many instances of xa xaconsumer are started at the same time,
        return com.arjuna.ats.jta.TransactionManager.transactionManager();
    }

    public static void main(String[] args) throws Exception {
        TransactionManager tm = TransactionManagerFactory.getInstance("ObjectStore", "ObjectStore");

    }
}
