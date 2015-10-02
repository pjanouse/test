package org.jboss.qa.hornetq.tools;

import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.constants.Constants;

/**
 * Created by mnovak on 4/14/15.
 *
 * @author mnovak@redhat.com
 */
public class ContainerUtils {

    /**
     *
     * Checks whether server is EAP 6 or not.
     *
     * @param container container
     * @return true is container is EAP6 type
     */
    public static boolean isEAP6(Container container) {
        return container.getContainerType().equals(Constants.CONTAINER_TYPE.EAP6_CONTAINER);
    }

    /**
     *
     * Checks whether server is EAP 7 or not.
     *
     * @param container container
     * @return true is container is EAP7 type
     */
    public static boolean isEAP7(Container container) {
        return container.getContainerType().equals(Constants.CONTAINER_TYPE.EAP7_CONTAINER);

    }
}
