package org.jboss.qa.management.cli;

/**
 * @author Petr Kremensky pkremens@redhat.com, Radim Hatlapatka rhatlapa@redhat.com
 */
public class CliUtils {

    /**
     * Create command from given address and operation without any attributes.
     *
     * @param address   Address where shall be command executed.
     * @param operation Name of operation which shall be executed.
     * @return Command as String from given address, operation and attributes.
     */
    public static String buildCommand(String address, String operation) {
        String command;
        if (address == null) {
            address = "";
        }
        if (operation == null) {
            throw new IllegalArgumentException("Operation cannot be null");
        }
        command = address + operation;
        return command;
    }


    /**
     * Create command from given address, operation and set of attributes.
     *
     * @param address   Address where shall be command executed.
     * @param operation Name of operation which shall be executed.
     * @param attribute Attribute for given operation.
     * @return Command as String from given address, operation and attribute.
     */
    public static String buildCommand(String address, String operation, String attribute) {
        if (address == null) {
            address = "";
        }
        if (operation == null) {
            throw new IllegalArgumentException("Operation cannot be null");
        }

        String command = address + operation;
        if (attribute != null) {
            command += "(" + attribute + ")";
        }
        return command;
    }

    /**
     * Create command from given address, operation and set of attributes.
     *
     * @param address    Address where shall be command executed.
     * @param operation  Name of operation which shall be executed.
     * @param attributes Array of attributes for given operation.
     * @return Command as String from given address, operation and attributes.
     */
    public static String buildCommand(String address, String operation, String[] attributes) {
        if (address == null) {
            address = "";
        }
        if (operation == null) {
            throw new IllegalArgumentException("Operation cannot be null");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(address);
        sb.append(operation);
        if (attributes != null) {
            sb.append("(");
            for (int i = 0; i < attributes.length; i++) {
                if (i == 0) {
                    sb.append(attributes[i]);
                } else {
                    sb.append("," + attributes[i]);
                }
            }
            sb.append(")");
        }
        return sb.toString();
    }
}