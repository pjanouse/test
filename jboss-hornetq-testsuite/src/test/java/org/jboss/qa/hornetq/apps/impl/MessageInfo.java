package org.jboss.qa.hornetq.apps.impl;

import java.io.Serializable;

/**
 * For lodh 5 test case.
 *
 * @author mnovak
 */
public class MessageInfo implements Serializable {

    private String name;

    private String address;

    public MessageInfo(String name, String address) {
        this.name = name;
        this.address = address;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the address
     */
    public String getAddress() {
        return address;
    }

    /**
     * @param address the address to set
     */
    public void setAddress(String address) {
        this.address = address;
    }
}
