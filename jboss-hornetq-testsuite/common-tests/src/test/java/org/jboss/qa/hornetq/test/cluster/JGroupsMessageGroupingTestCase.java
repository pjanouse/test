package org.jboss.qa.hornetq.test.cluster;


import category.MessageGrouping;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.junit.experimental.categories.Category;

@Category(MessageGrouping.class)
@Prepare(value = "FourNodes", params = {
        @Param(name = PrepareParams.CLUSTER_TYPE, value = "JGROUPS_DISCOVERY")
})
public class JGroupsMessageGroupingTestCase extends MessageGroupingTestCase {

}
