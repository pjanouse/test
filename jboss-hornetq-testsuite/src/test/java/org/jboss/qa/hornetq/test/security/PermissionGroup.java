package org.jboss.qa.hornetq.test.security;


/**
 * Permission groups for HornetQ security settings.
 *
 * See HornetQ documentation, section 31.1, for more details.
 *
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public enum PermissionGroup {

    CREATE_DURABLE_QUEUE("create-durable-queue"),
    DELETE_DURABLE_QUEUE("delete-durable-queue"),
    CREATE_NONDURABLE_QUEUE("create-non-durable-queue"),
    DELETE_NONDURABLE_QUEUE("delete-non-durable-queue"),
    SEND("send"),
    CONSUME("consume"),
    MANAGE("manage");

    private String key;


    private PermissionGroup(final String key) {
        this.key = key;
    }


    public String getKey() {
        return this.key;
    }

}
