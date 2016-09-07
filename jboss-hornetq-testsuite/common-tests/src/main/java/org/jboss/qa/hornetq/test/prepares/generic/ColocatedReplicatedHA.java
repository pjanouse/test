package org.jboss.qa.hornetq.test.prepares.generic;

import org.jboss.qa.PrepareMethod;
import org.jboss.qa.PrepareUtils;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.JMSOperations;

import java.util.Map;

public class ColocatedReplicatedHA extends ColocatedSharedStoreHA {

    @Override
    @PrepareMethod(value = "ColocatedReplicatedHA", labels = {"EAP6"})
    public void prepareMethodEAP6(Map<String, Object> params) throws Exception {
        super.prepareMethodEAP6(params);
    }

    @Override
    @PrepareMethod(value = "ColocatedReplicatedHA", labels = {"EAP7"})
    public void prepareMethodEAP7(Map<String, Object> params) throws Exception {
        super.prepareMethodEAP7(params);
    }

    @Override
    protected void afterPrepareContainerEAP6(Map<String, Object> params, Container container) throws Exception {
        Constants.CLUSTER_TYPE clusterType = Constants.CLUSTER_TYPE.valueOf(PrepareUtils.getString(params, PrepareParams.CLUSTER_TYPE));
        int maxSavedReplicatedJournalSize = PrepareUtils.getInteger(params, PrepareParams.MAX_SAVED_REPLICATED_JOURNAL_SIZE, 2);
        super.afterPrepareContainerEAP6(params, container);

        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.setFailoverOnShutdown(true);
        jmsOperations.setSharedStore(false);
        jmsOperations.setFailoverOnShutdown(true);
        jmsOperations.setCheckForLiveServer(true);
        jmsOperations.setMaxSavedReplicatedJournals(maxSavedReplicatedJournalSize);

        jmsOperations.setJournalDirectoryPath("journal-live");
        jmsOperations.setBindingsDirectoryPath("bindings-live");
        jmsOperations.setPagingDirectoryPath("paging-live");
        jmsOperations.setLargeMessagesDirectoryPath("largemessages-live");

        jmsOperations.close();
        container.restart();
        Thread.sleep(2000);
        jmsOperations = container.getJmsOperations();

        prepareBackupEAP6(params, container);

        jmsOperations.setSharedStore(BACKUP_SERVER_NAME, false);
        jmsOperations.setCheckForLiveServer(true, BACKUP_SERVER_NAME);

        if (clusterType != Constants.CLUSTER_TYPE.STATIC_CONNECTORS) {
            prepareCluster(params, jmsOperations, BACKUP_SERVER_NAME);
        }

        jmsOperations.close();
    }

    @Override
    protected void afterPrepareContainer1EAP6(Map<String, Object> params, Container container) throws Exception {
        afterPrepareContainerEAP6(params, container);
    }

    @Override
    protected void afterPrepareContainer2EAP6(Map<String, Object> params, Container container) throws Exception {
        afterPrepareContainerEAP6(params, container);
    }

    @Override
    protected void afterPrepareEAP6(Map<String, Object> params) throws Exception {
        super.afterPrepareEAP6(params);

        Container container1 = getContainer(params, 1);
        Container container2 = getContainer(params, 2);

        JMSOperations jmsOperations = container1.getJmsOperations();

        jmsOperations.setBackupGroupName("group0");
        jmsOperations.setBackupGroupName("group1", BACKUP_SERVER_NAME);

        jmsOperations.close();

        jmsOperations = container2.getJmsOperations();

        jmsOperations.setBackupGroupName("group1");
        jmsOperations.setBackupGroupName("group0", BACKUP_SERVER_NAME);

        jmsOperations.close();
    }

    @Override
    protected void afterPrepareContainerEAP7(Map<String, Object> params, Container container) throws Exception {
        super.afterPrepareContainerEAP7(params, container);

        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.setJournalDirectoryPath(BACKUP_SERVER_NAME, "journal-backup");
        jmsOperations.setBindingsDirectoryPath(BACKUP_SERVER_NAME, "bindings-backup");
        jmsOperations.setPagingDirectoryPath(BACKUP_SERVER_NAME, "paging-backup");
        jmsOperations.setLargeMessagesDirectoryPath(BACKUP_SERVER_NAME, "largemessages-backup");

        jmsOperations.close();
    }

    @Override
    protected void afterPrepareContainer1EAP7(Map<String, Object> params, Container container) throws Exception {
        afterPrepareContainerEAP7(params, container);
    }

    @Override
    protected void afterPrepareContainer2EAP7(Map<String, Object> params, Container container) throws Exception {
        afterPrepareContainerEAP7(params, container);
    }

    @Override
    protected void afterPrepareEAP7(Map<String, Object> params) throws Exception {
        int maxSavedReplicatedJournalSize = PrepareUtils.getInteger(params, PrepareParams.MAX_SAVED_REPLICATED_JOURNAL_SIZE, 2);

        super.afterPrepareEAP7(params);

        Container container1 = getContainer(params, 1);
        Container container2 = getContainer(params, 2);

        JMSOperations jmsOperations = container1.getJmsOperations();

        jmsOperations.removeHAPolicy(SERVER_NAME);
        jmsOperations.removeHAPolicy(BACKUP_SERVER_NAME);

        jmsOperations.addHAPolicyReplicationMaster(true, CLUSTER_NAME, "group0");
        jmsOperations.addHAPolicyReplicationSlave(BACKUP_SERVER_NAME, true, CLUSTER_NAME, 0, "group1", maxSavedReplicatedJournalSize, true, false, null, null, null, null);

        jmsOperations.close();

        jmsOperations = container2.getJmsOperations();

        jmsOperations.removeHAPolicy(SERVER_NAME);
        jmsOperations.removeHAPolicy(BACKUP_SERVER_NAME);

        jmsOperations.addHAPolicyReplicationMaster(true, CLUSTER_NAME, "group1");
        jmsOperations.addHAPolicyReplicationSlave(BACKUP_SERVER_NAME, true, CLUSTER_NAME, 0, "group0", maxSavedReplicatedJournalSize, true, false, null, null, null, null);

        jmsOperations.close();
    }
}
