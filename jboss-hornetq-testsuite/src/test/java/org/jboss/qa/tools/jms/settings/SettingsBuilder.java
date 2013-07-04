package org.jboss.qa.tools.jms.settings;


import org.jboss.qa.tools.JMSOperations;


/**
 *
 * @author <a href="mailto:msvehla@redhat.com">Martin Svehla</a>
 */
public interface SettingsBuilder {

    void create();


    JMSOperations getJmsOperations();


    SettingsBuilder withQueue(final String queueName, final boolean isDurable);


    SettingsBuilder withClustering(final String bindingAddress);


    //SettingsBuilder withClustering(groupName, broadCastGroup, discoveryGroup, connectorName


    SettingsBuilder withoutClustering();


    SettingsBuilder withJdbcDriver(final String driverName, final String moduleName,
            final String driverClass, final String xaDataSource);


    /**
     *
     * @param maxSize  in bytes
     * @param pageSize in bytes
     *
     * @return
     */
    SettingsBuilder withPaging(final int maxSize, final long pageSize);


    /**
     *
     * @param maxSize             in bytes
     * @param pageSize            in bytes
     * @param redeliveryDelay     in milliseconds
     * @param redistributionDelay in milliseconds
     *
     * @return
     */
    SettingsBuilder withPaging(final int maxSize, final long pageSize,
            final int redeliveryDelay, final long redistributionDelay);


    SettingsBuilder withPersistence();


    SettingsBuilder withoutPersistence();


    //SettingsBuilder withoutSecurity();


    SettingsBuilder withSharedStore();


    SettingsBuilder withoutSharedStore();


    SettingsBuilder withTransactionIdentifier(final int identifier);


    SettingsBuilder withXaDataSource(final String jndiName, final String poolName,
            final String driverName, final String xaDataSourceClass,
            final DataSourceProperties properties);
    /*SettingsBuilder withXaDataSource(final String jndiName, final String poolName, final boolean useJavaContext,
     final boolean useCCM, final String driverName, final String transactionIsolation,
     final String xaDataSourceClass, final boolean isSameRmOverride, final boolean noTxSeparatePool,
     final DataSourceProperties properties);*/

}
