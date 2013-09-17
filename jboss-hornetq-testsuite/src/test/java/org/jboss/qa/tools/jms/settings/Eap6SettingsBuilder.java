package org.jboss.qa.tools.jms.settings;


import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.qa.tools.ArquillanDescriptorHelper;
import org.jboss.qa.tools.HornetQAdminOperationsEAP6;
import org.jboss.qa.tools.JMSOperations;


/**
 *
 * @author <a href="mailto:msvehla@redhat.com">Martin Svehla</a>
 */
class Eap6SettingsBuilder implements SettingsBuilder {

    private final String node;

    private final HornetQAdminOperationsEAP6 ops;
    //private final ArquillianDescriptor descriptor;


    public Eap6SettingsBuilder(final String containerName, final ArquillianDescriptor arquillianDescriptor) {
        if (ArquillanDescriptorHelper.isDefaultContainer(containerName, arquillianDescriptor)) {
            this.node = "default";
        } else {
            this.node = containerName;
        }

        this.ops = new HornetQAdminOperationsEAP6();
        this.ops.setHostname(ArquillanDescriptorHelper.getContainerHostname(containerName, arquillianDescriptor));
        this.ops.setPort(ArquillanDescriptorHelper.getContainerPort(containerName, arquillianDescriptor));
        this.ops.connect();
    }


    public Eap6SettingsBuilder(final JMSOperations operations) {
        this.node = "default";

        if (operations instanceof HornetQAdminOperationsEAP6) {
            this.ops = (HornetQAdminOperationsEAP6) operations;
        } else {
            throw new IllegalArgumentException("Incompatible operations provider (expected "
                    + HornetQAdminOperationsEAP6.class.getName() + ", got " + operations.getClass().getName() + ")");
        }
    }


    @Override
    public void create() {
        this.ops.close();
    }


    @Override
    public JMSOperations getJmsOperations() {
        return this.ops;
    }


    @Override
    public SettingsBuilder withQueue(String queueName, boolean isDurable) {
        try {
            this.ops.removeQueue(queueName);
        } catch (Exception ignored) {
        }

        this.ops.createQueue(this.node, queueName, this.createQueueJndiName(queueName), isDurable);
        return this;
    }


    @Override
    public SettingsBuilder withClustering(final String bindingAddress) {
        this.ops.setClustered(this.node, true);
        this.ops.setInetAddress("public", bindingAddress);
        this.ops.setInetAddress("unsecure", bindingAddress);
        this.ops.setInetAddress("management", bindingAddress);
        return this;
    }


    @Override
    public SettingsBuilder withoutClustering() {
        this.ops.setClustered(this.node, false);
        this.ops.removeClusteringGroup("my-cluster");
        this.ops.removeBroadcastGroup("bg-group1");
        this.ops.removeDiscoveryGroup("dg-group1");
        return this;
    }


    @Override
    public SettingsBuilder withJdbcDriver(final String driverName, final String moduleName, final String driverClass,
            final String xaDataSource) {
        this.ops.createJDBCDriver(driverName, moduleName, driverClass, xaDataSource);
        return this;
    }


    @Override
    public SettingsBuilder withXaDataSource(final String jndiName, final String poolName, final String driverName,
            final String xaDataSourceClass, final DataSourceProperties properties) {

        this.ops.createXADatasource(jndiName, poolName, false, false, driverName, "TRANSACTION_READ_COMMITED",
                xaDataSourceClass, false, true);
        if (properties != null) {
            this.updateDataSourceProperties(poolName, properties);
        }
        return this;
    }


    @Override
    public SettingsBuilder withPaging(final int maxSize, final long pageSize) {
        return this.withPaging(maxSize, pageSize, 0, 0);
    }


    @Override
    public SettingsBuilder withPaging(final int maxSize, final long pageSize,
            final int redeliveryDelay, final long redistributionDelay) {

        this.ops.removeAddressSettings(this.node, "#");
        this.ops.addAddressSettings(this.node, "#", "PAGE", maxSize, redeliveryDelay, redistributionDelay, pageSize);
        return this;
    }


    @Override
    public SettingsBuilder withPersistence() {
        this.ops.setPersistenceEnabled(this.node, true);
        return this;
    }


    @Override
    public SettingsBuilder withoutPersistence() {
        this.ops.setPersistenceEnabled(this.node, false);
        return this;
    }


    @Override
    public SettingsBuilder withSharedStore() {
        this.ops.setSharedStore(this.node, true);
        return this;
    }


    @Override
    public SettingsBuilder withoutSharedStore() {
        this.ops.setSharedStore(this.node, false);
        return this;
    }


    @Override
    public SettingsBuilder withTransactionIdentifier(final int identifier) {
        this.ops.setNodeIdentifier(identifier);
        return this;
    }


    private String createQueueJndiName(final String queueName) {
        return "jms/queue/" + queueName;
    }


    private void updateDataSourceProperties(final String dsName, final DataSourceProperties props) {
        for (String dsKey : props.getDataSourcePropertiesKeys()) {
            this.ops.addDatasourceProperty(dsName, dsKey, props.getDataSourceProperty(dsKey));
        }

        for (String xaKey : props.getXaPropertiesKeys()) {
            this.ops.addXADatasourceProperty(dsName, xaKey, props.getXaProperty(xaKey));
        }
    }

}
