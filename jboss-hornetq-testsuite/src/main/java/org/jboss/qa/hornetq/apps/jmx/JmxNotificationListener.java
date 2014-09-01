package org.jboss.qa.hornetq.apps.jmx;


import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.management.Notification;
import javax.management.NotificationListener;

import org.apache.log4j.Logger;


/**
 * Simple JMX listener that will catch any notification on the MBean it's listening to.
 *
 * To
 */
public class JmxNotificationListener implements NotificationListener {

    private static final Logger LOG = Logger.getLogger(JmxNotificationListener.class);

    private final List<Notification> caughtNotifications = new LinkedList<Notification>();

    @Override
    public void handleNotification(Notification notification, Object handback) {
        LOG.info("JMX notification (type " + notification.getType() + "): " + notification.getMessage());
        caughtNotifications.add(notification);
    }

    public List<Notification> getCaughtNotifications() {
        return Collections.unmodifiableList(caughtNotifications);
    }

}
