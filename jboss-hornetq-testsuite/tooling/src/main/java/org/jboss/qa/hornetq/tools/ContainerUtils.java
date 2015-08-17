package org.jboss.qa.hornetq.tools;

import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCaseConstants;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.impl.ArtemisJMSImplementation;
import org.jboss.qa.hornetq.apps.impl.HornetqJMSImplementation;

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
        return container.getContainerType().equals(HornetQTestCaseConstants.CONTAINER_TYPE.EAP6_CONTAINER);
    }

    /**
     *
     * Checks whether server is EAP 7 or not.
     *
     * @param container container
     * @return true is container is EAP7 type
     */
    public static boolean isEAP7(Container container) {
        return container.getContainerType().equals(HornetQTestCaseConstants.CONTAINER_TYPE.EAP7_CONTAINER);

    }

    public static JMSImplementation getJMSImplementation(Container container) {
        if (isEAP7(container)) {
            return new ArtemisJMSImplementation();
        } else {
            return new HornetqJMSImplementation();
        }
    }
}
