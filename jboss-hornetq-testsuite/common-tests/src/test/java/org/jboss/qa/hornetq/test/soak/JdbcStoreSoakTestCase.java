package org.jboss.qa.hornetq.test.soak;

import category.SoakJdbcStore;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.prepares.PrepareMethods;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.junit.Assume;
import org.junit.experimental.categories.Category;

import java.util.HashMap;
import java.util.Map;

/**
 * @tpChapter PERFORMANCE TESTING
 * @tpSubChapter HORNETQ SOAK TEST
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-soak-test/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19046/activemq-artemis-performance#testcases
 * <p>
 * Reconfigure {@link NewSoakTestCase} to use JDBC store.
 * <p>
 * This test doesnt use Prepare framework because configuration is complicated. It only uses database prepare method.
 * <p>
 * Default database is Oracle12c. Use property -Dprepare.param.DATABASE to select desired database.
 * <p>
 * Created by mstyk on 1/3/17.
 */
@Category(SoakJdbcStore.class)
public class JdbcStoreSoakTestCase extends NewSoakTestCase {

    /**
     * This runs only with EAP7
     */
    @Override
    protected void makeAssumption() {
        Assume.assumeTrue(this.getClass().getSimpleName() + "runs only with EAP7 " , container(1).getContainerType().equals(Constants.CONTAINER_TYPE.EAP7_CONTAINER));
    }

    @Override
    protected void setupJmsServer(final Container container) throws Exception {
        String connectorName = ContainerUtils.isEAP6(container) ? "netty" : "http-connector";

        JMSOperations ops = container.getJmsOperations();

        if (ContainerUtils.isEAP6(container)) {
            ops.setClustered(true);
            ops.setSharedStore(true);
        }

        ops.disableSecurity();
        ops.addLoggerCategory("com.arjuna", "ERROR");

        ops.removeBroadcastGroup("bg-group1");
        ops.setBroadCastGroup("bg-group1", "messaging-group", 2000, connectorName, "");

        ops.removeDiscoveryGroup("dg-group1");
        ops.setDiscoveryGroup("dg-group1", "messaging-group", 10000);

        ops.removeClusteringGroup("my-cluster");
        ops.setClusterConnections("my-cluster", "jms", "dg-group1", false, 1, 1000, true, connectorName);

        ops.removeAddressSettings("#");
        ops.addAddressSettings("#", "BLOCK", 10485760, 0, 0, 2097152); //Paging doesnt work

        //https://issues.jboss.org/browse/JBEAP-7454
        ops.setMinLargeMessageSizeOnConnectionFactory(Constants.CONNECTION_FACTORY_EAP7, 1024000);
        ops.setMinLargeMessageSizeOnConnectionFactory(Constants.IN_VM_CONNECTION_FACTORY_EAP7, 1024000);
        ops.setMinLargeMessageSizeOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, 1024000);

        ops.close();

        prepareJdbcStorage(container);
    }


    @Override
    protected void setupMdbServer(final Container container) throws Exception {
        String connectorName = ContainerUtils.isEAP6(container) ? "netty" : "http-connector";

        JMSOperations ops = container.getJmsOperations();

        if (ContainerUtils.isEAP6(container)) {
            ops.setClustered(true);
            ops.setSharedStore(true);
        }

        ops.disableSecurity();
        ops.addLoggerCategory("com.arjuna", "ERROR");

        ops.removeBroadcastGroup("bg-group1");
        ops.setBroadCastGroup("bg-group1", "messaging-group", 2000, connectorName, "");

        ops.removeDiscoveryGroup("dg-group1");
        ops.setDiscoveryGroup("dg-group1", "messaging-group", 10000);

        ops.removeClusteringGroup("my-cluster");
        ops.setClusterConnections("my-cluster", "jms", "dg-group1", false, 1, 1000, true, connectorName);

        ops.removeAddressSettings("#");
        ops.addAddressSettings("#", "BLOCK", 10485760, 0, 0, 2097152); //Paging doesnt work

        //https://issues.jboss.org/browse/JBEAP-7454
        ops.setMinLargeMessageSizeOnConnectionFactory(Constants.CONNECTION_FACTORY_EAP7, 1024000);
        ops.setMinLargeMessageSizeOnConnectionFactory(Constants.IN_VM_CONNECTION_FACTORY_EAP7, 1024000);
        ops.setMinLargeMessageSizeOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, 1024000);

        ops.close();

        prepareJdbcStorage(container);
    }

    private void prepareJdbcStorage(Container container) throws Exception {
        String databaseName = System.getProperty("prepare.param.DATABASE", "oracle12c");

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("container", container);
        params.put(PrepareParams.DATABASE, databaseName);

        new PrepareMethods().prepareDatabase(params);
    }
}
