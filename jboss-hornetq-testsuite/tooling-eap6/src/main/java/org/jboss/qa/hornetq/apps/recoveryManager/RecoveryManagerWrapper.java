package org.jboss.qa.hornetq.apps.recoveryManager;

import com.arjuna.ats.arjuna.common.CoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.RecoveryEnvironmentBean;
import com.arjuna.ats.arjuna.recovery.RecoveryManager;
import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mnovak on 10/8/14.
 * <p/>
 * Start recovery manager. Used only in tests with recovery manager on client side.
 *
 * @author mnovak@redhat.com
 */
public class RecoveryManagerWrapper {

    private static final Logger logger = Logger.getLogger(RecoveryManagerWrapper.class);

    // singleton
    protected static RecoveryManager recoveryManager = null;

    // this lock is used so we can synchronize recovery scans among XaConsumers, access to recovery manager
    public static Object recoveryManagerLock = new Object();

    private RecoveryManagerWrapper() {
    }

    public static RecoveryManagerWrapper getInstance(String resourceRecoveryClass, String remoteResourceRecoveryOpts, String objectStore, String nodeIdentifier) throws Exception {
        if (recoveryManager == null) {
            recoveryManager = createRecoveryManager(resourceRecoveryClass, remoteResourceRecoveryOpts, objectStore, nodeIdentifier);
        }

        logger.info("Creating recovery manager which has ObjectStore in location: " + new File(objectStore).getAbsolutePath());

        return new RecoveryManagerWrapper();
    }

    public static RecoveryManager createRecoveryManager(String resourceRecoveryClass, String remoteResourceRecoveryOpts, String objectStore, String nodeIdentifier) throws Exception {

//        String resourceRecoveryClass = HornetQXAResourceRecovery.class.getName();
//            String remoteResourceRecoveryOpts = "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory," +
//                    "guest,guest,host=" + HornetQTestCase.getHostname(CONTAINER1_NAME) + ",port=" + HornetQTestCase.getHornetqPort(CONTAINER1_NAME) + ";org.hornetq.core.remoting.impl.netty.NettyConnectorFactory,guest,guest,host="
//                    + HornetQTestCase.getHostname(CONTAINER2_NAME) + " ,port=" + HornetQTestCase.getHornetqPort(CONTAINER2_NAME);
//        String remoteResourceRecoveryOpts = "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory," +
//                "guest,guest,host=127.0.0.1,port=5445;org.hornetq.core.remoting.impl.netty.NettyConnectorFactory,guest,guest,host=127.0.0.1,port=7445";

        //org.hornetq.core.remoting.impl.netty.NettyConnectorFactory,guest,guest,host=localhost,port=5445;org.hornetq.core.remoting.impl.netty.NettyConnectorFactory,guest,guest,host=localhost1,port=5446"
        List<String> recoveryClassNames = new ArrayList<String>();
        recoveryClassNames.add(resourceRecoveryClass + ";" + remoteResourceRecoveryOpts);

        // this appears that it does not work
        BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class).setObjectStoreDir(objectStore);
        BeanPopulator.getDefaultInstance(CoreEnvironmentBean.class).setNodeIdentifier(nodeIdentifier);

        BeanPopulator.getDefaultInstance(JTAEnvironmentBean.class).setXaResourceRecoveryClassNames(recoveryClassNames);
        BeanPopulator.getDefaultInstance(JTAEnvironmentBean.class).setXaAssumeRecoveryComplete(true);
        BeanPopulator.getDefaultInstance(RecoveryEnvironmentBean.class).setRecoveryBackoffPeriod(1);

        RecoveryManager.delayRecoveryManagerThread();

        // one means that i'm in charge of running recovery scans
        RecoveryManager rm = RecoveryManager.manager(RecoveryManager.DIRECT_MANAGEMENT);
        rm.initialize(); // for initilize it again in case that it was terminated before
        return rm;
    }


    /**
     * Stop recovery manager.
     */
    public static void stopRecovery() {
        try {
            synchronized (recoveryManagerLock) {
                if (recoveryManager != null) {
                    recoveryManager.terminate();
                    recoveryManager = null;
                    logger.info("Recovery manager was stopped.");
                }
            }
        } catch (Exception ex) {
            logger.error("Exception when calling stopRecovery.", ex);
        }
    }

    /**
     * This run recovery scan and blocks until it's complete.
     */
    public static void runRecoveryScan() throws Exception {
        synchronized (recoveryManagerLock) {
            if (recoveryManager == null) {
                throw new Exception("Recovery manager is null. Probably was not initialized or was terminated.");
            }
            logger.info("Running recovery scan.");
            recoveryManager.scan();
        }
    }

    public static void main(String[] args) throws Exception {

        String resourceRecoveryClass = "org.hornetq.jms.server.recovery.HornetQXAResourceRecovery";
        String remoteResourceRecoveryOpts = "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory," +
                "guest,guest,host=127.0.0.1,port=5445;org.hornetq.core.remoting.impl.netty.NettyConnectorFactory,guest,guest,host=127.0.0.1,port=7445";

        RecoveryManagerWrapper recoveryManagerWrapper = RecoveryManagerWrapper.getInstance(resourceRecoveryClass, remoteResourceRecoveryOpts, "ObjectStore", "ObjectStore");
        for (int i = 0; i < 10; i++ ) {
            recoveryManagerWrapper.runRecoveryScan();
        }
        recoveryManagerWrapper.stopRecovery();
//        recoveryManagerWrapper = getInstance(resourceRecoveryClass, remoteResourceRecoveryOpts, "ObjectStore", "ObjectStore");
//        recoveryManagerWrapper.runRecoveryScan();
//        recoveryManagerWrapper.stopRecovery();


    }
}
