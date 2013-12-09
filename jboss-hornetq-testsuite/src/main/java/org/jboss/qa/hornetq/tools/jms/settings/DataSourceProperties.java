package org.jboss.qa.hornetq.tools.jms.settings;


import java.util.HashMap;
import java.util.Map;
import java.util.Set;


/**
 *
 * @author <a href="mailto:msvehla@redhat.com">Martin Svehla</a>
 */
public class DataSourceProperties {

    private final String dataSourceName;

    private final Map<String, String> dataSourceProperties = new HashMap<String, String>();

    private final Map<String, String> xaProperties = new HashMap<String, String>();


    public static Builder forDataSource(final String dataSourceName) {
        return new Builder(dataSourceName);
    }


    private DataSourceProperties(final String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }


    public String getDataSourceName() {
        return this.dataSourceName;
    }


    public void setDataSourceProperty(final String key, final String value) {
        this.dataSourceProperties.put(key, value);
    }


    public void setXaProperty(final String key, final String value) {
        this.xaProperties.put(key, value);
    }


    public String getDataSourceProperty(final String key) {
        return this.dataSourceProperties.get(key);
    }


    public Set<Map.Entry<String, String>> getDataSourcePropertiesKeySet() {
        return this.dataSourceProperties.entrySet();
    }


    public Set<String> getDataSourcePropertiesKeys() {
        return this.dataSourceProperties.keySet();
    }


    public String getXaProperty(final String key) {
        return this.xaProperties.get(key);
    }


    public Set<Map.Entry<String, String>> getXaPropertiesKeySet() {
        return this.xaProperties.entrySet();
    }


    public Set<String> getXaPropertiesKeys() {
        return this.xaProperties.keySet();
    }


    public static final class Builder {

        private final DataSourceProperties props;


        private Builder(final String dataSourceName) {
            this.props = new DataSourceProperties(dataSourceName);
        }


        public Builder withDataSourceProperty(final DsKeys key, final String value) {
            this.props.dataSourceProperties.put(key.getKey(), value);
            return this;
        }


        public Builder withXaProperty(final XaKeys key, final String value) {
            this.props.xaProperties.put(key.getKey(), value);
            return this;
        }


        public DataSourceProperties create() {
            return this.props;
        }

    }


    public static enum DsKeys {

        USE_JAVA_CONTEXT("use-java-context"),
        USE_CCM("use-ccm"),
        TRANSACTION_ISOLATION("transaction-isolation"),
        NO_TX_SEPARATE_POOL("no-tx-separate-pool"),
        SAME_RM_OVERRIDE("same-rm-override"),
        MIN_POOL_SIZE("min-pool-size"),
        MAX_POOL_SIZE("max-pool-size"),
        POOL_PREFILL("pool-prefill"),
        POOL_USE_STRICT_MIN("pool-use-strict-min"),
        FLUSH_STRATEGY("flush-strategy"),
        VALID_CONNECTION_CHECKER_CLASS_NAME("valid-connection-checker-class-name"),
        VALIDATE_ON_MATCH("validate-on-match"),
        BACKGROUND_VALIDATION("background-validation"),
        BLOCKING_TIMEOUT_WAIT_MILLIS("blocking-timeout-wait-millis"),
        IDLE_TIMEOUT_MINUTES("idle-timeout-minutes"),
        PREPARED_STATEMENTS_CACHE_SIZE("prepared-statements-cache-size"),
        EXCEPTION_SORTER_CLASS_NAME("exception-sorter-class-name"),
        USE_TRY_LOCK("use-try-lock");

        private String key;


        private DsKeys(final String key) {
            this.key = key;
        }


        public String getKey() {
            return this.key;
        }

    }


    public static enum XaKeys {

        DATABASE_NAME("DatabaseName"),
        PASSWORD("Password"),
        SERVER_NAME("ServerName"),
        USER("User"),
        URL("URL");

        private String key;


        private XaKeys(final String key) {
            this.key = key;
        }


        public String getKey() {
            return this.key;
        }

    }

}
