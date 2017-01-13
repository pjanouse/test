package org.jboss.qa.hornetq.test.failover;

import category.FailoverColocatedCluster;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

/**
 * Created by eduda on 13.1.2017.
 */
@RunWith(Arquillian.class)
@Prepare(params = {
        @Param(name = PrepareParams.CONNECTOR_TYPE, value = "NETTY_NIO")
})
@Category(FailoverColocatedCluster.class)
public class NettyColocatedClusterFailoverTestCase extends ColocatedClusterFailoverTestCase {
}
