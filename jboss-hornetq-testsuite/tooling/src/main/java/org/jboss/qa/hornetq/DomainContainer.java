package org.jboss.qa.hornetq;

import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.qa.hornetq.constants.Constants;


/**
 * Object representation of host controller in EAP domain mode
 */
public interface DomainContainer {

    /**
     * Allows to initialize domain container representation with the contents of arquillian.xml
     *
     * @param arquillianDescriptor arquillian.xml representation
     * @param containerController ARQ controller for manipulating host controller and individual nodes
     */
    void init(ArquillianDescriptor arquillianDescriptor, ContainerController containerController);

    // ===== General operations =====

    /**
     * @return TYPE OF THE CONTAINER
     */
    Constants.CONTAINER_TYPE getContainerType();

    /**
     * @return Host controller hostname or IP
     */
    String getHostname();

    /**
     * @return Host controller management port
     */
    int getPort();

    // ===== Server group operations =====

    /**
     * Create new server group with from given configuration profile and socket bindings.
     *
     * @param groupName Server group name
     * @param profileName Configuration profile
     * @param socketBindings Server group socket bindings
     */
    void createServerGroup(String groupName, String profileName, String socketBindings);

    /**
     * Remove server group.
     *
     * Note: This does not remove the server nodes that belong to this group. If you want to delete those as well, you have to
     * call {@link DomainServerGroup#deleteNode(String)} first.
     *
     * @param groupName Server group name
     */
    void removeServerGroup(String groupName);

    /**
     * Get object representation of server group with given name.
     *
     * @param groupName Server group name
     * @return Server group representation
     */
    DomainServerGroup serverGroup(String groupName);

    // ===== Domain management =====

    /**
     * Reload host controller.
     *
     * This is required after changing server groups and server nodes settings (especially moving servers between server
     * groups).
     */
    void reloadDomain();

}
