// TODO REFACTOR FOR EAP 7
package org.jboss.qa.hornetq.apps.jmx;

import org.jboss.logging.Logger;
import org.kohsuke.MetaInfServices;

import javax.management.Notification;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;


/**
 * Simple JMX listener that will catch any notification on the MBean it's listening to.
 */
@MetaInfServices
public class JmxNotificationListenerImplEAP7 implements JmxNotificationListener {

    private static final Logger LOG = Logger.getLogger(JmxNotificationListenerImplEAP7.class);

    private final List<Notification> caughtNotifications = new LinkedList<Notification>();

    public JmxNotificationListenerImplEAP7() {

    }

    @Override
    public void handleNotification(Notification notification, Object handback) {
        LOG.info("JMX notification (type " + notification.getType() + "): " + notification.getMessage());
        caughtNotifications.add(notification);
    }

    /**
     * @param notificationsCount
     * @param timeout            milliseconds
     * @throws InterruptedException
     */
    @Override
    public void waitForNotificationsCount(int notificationsCount, long timeout) throws InterruptedException {
        long start = System.currentTimeMillis();
        long duration = 0;

        while (getCaughtNotifications().size() < notificationsCount && duration < timeout) {
            Thread.sleep(100);
            duration = System.currentTimeMillis() - start;
        }


    }

    @Override
    public List<Notification> getCaughtNotifications() {
        return Collections.unmodifiableList(caughtNotifications);
    }

}
