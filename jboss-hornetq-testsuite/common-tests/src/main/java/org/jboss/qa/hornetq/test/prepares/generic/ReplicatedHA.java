package org.jboss.qa.hornetq.test.prepares.generic;

import org.jboss.qa.PrepareContext;
import org.jboss.qa.PrepareMethod;
import org.jboss.qa.PrepareUtils;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.JMSOperations;

import java.util.Map;

public class ReplicatedHA extends TwoNodes {

    @Override
    @PrepareMethod(value = "ReplicatedHA", labels = {"EAP6", "EAP7"})
    public void prepareMethod(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.prepareMethod(params, ctx);
    }

    @Override
    protected void beforePrepare(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.beforePrepare(params, ctx);
        PrepareUtils.setIfNotSpecified(params, "1." + PrepareParams.HA_TYPE, Constants.HA_TYPE.REPLICATION_MASTER);
        PrepareUtils.setIfNotSpecified(params, "2." + PrepareParams.HA_TYPE, Constants.HA_TYPE.REPLICATION_SLAVE);
        PrepareUtils.setIfNotSpecified(params, PrepareParams.REPLICATION_GROUP_NAME, "group0");
    }
}
