package org.jboss.qa.hornetq.test.prepares.generic;

import org.jboss.qa.PrepareMethod;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.tools.JMSOperations;

import java.util.Map;

public class RemoteJCAReplicated extends RemoteJCASharedStore {

    @Override
    @PrepareMethod(value = "RemoteJCAReplicated", labels = {"EAP6"})
    public void prepareMethodEAP6(Map<String, Object> params) throws Exception {
        super.prepareMethodEAP6(params);
    }

    @Override
    @PrepareMethod(value = "RemoteJCAReplicated", labels = {"EAP7"})
    public void prepareMethodEAP7(Map<String, Object> params) throws Exception {
        super.prepareMethodEAP7(params);
    }

    @Override
    protected void afterPrepareContainer1EAP6(Map<String, Object> params, Container container) throws Exception {
        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.setFailoverOnShutdown(true);
        jmsOperations.setSharedStore(false);
        jmsOperations.setBackupGroupName("group-0");
        jmsOperations.setCheckForLiveServer(true);

        jmsOperations.close();
    }

    @Override
    protected void afterPrepareContainer2EAP6(Map<String, Object> params, Container container) throws Exception {
        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.setBackup(true);
        jmsOperations.setFailoverOnShutdown(true);
        jmsOperations.setSharedStore(false);
        jmsOperations.setBackupGroupName("group-0");
        jmsOperations.setCheckForLiveServer(true);

        jmsOperations.close();
    }

    @Override
    protected void afterPrepareContainer1EAP7(Map<String, Object> params, Container container) throws Exception {
        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.addHAPolicyReplicationMaster(true, CLUSTER_NAME, "group-0");

        jmsOperations.close();
    }

    @Override
    protected void afterPrepareContainer2EAP7(Map<String, Object> params, Container container) throws Exception {
        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.addHAPolicyReplicationSlave(true, CLUSTER_NAME, 0, "group-0", 10, true, false, null, null, null, null);

        jmsOperations.close();
    }
}
