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

public class ReplicatedHAFourNodes extends FourNodes {

    @Override
    @PrepareMethod(value = "ReplicatedHAFourNodes", labels = {"EAP6", "EAP7"})
    public void prepareMethod(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.prepareMethod(params, ctx);
    }

    @Override
    protected void beforePrepare(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.beforePrepare(params, ctx);
        PrepareUtils.setIfNotSpecified(params, "1." + PrepareParams.HA_TYPE, Constants.HA_TYPE.REPLICATION_MASTER);
        PrepareUtils.setIfNotSpecified(params, "2." + PrepareParams.HA_TYPE, Constants.HA_TYPE.REPLICATION_SLAVE);
        PrepareUtils.setIfNotSpecified(params, "3." + PrepareParams.HA_TYPE, Constants.HA_TYPE.REPLICATION_MASTER);
        PrepareUtils.setIfNotSpecified(params, "4." + PrepareParams.HA_TYPE, Constants.HA_TYPE.REPLICATION_SLAVE);

        PrepareUtils.setIfNotSpecified(params, "1." + PrepareParams.REPLICATION_GROUP_NAME, "group0");
        PrepareUtils.setIfNotSpecified(params, "2." + PrepareParams.REPLICATION_GROUP_NAME, "group0");
        PrepareUtils.setIfNotSpecified(params, "3." + PrepareParams.REPLICATION_GROUP_NAME, "group1");
        PrepareUtils.setIfNotSpecified(params, "4." + PrepareParams.REPLICATION_GROUP_NAME, "group1");
    }

}
