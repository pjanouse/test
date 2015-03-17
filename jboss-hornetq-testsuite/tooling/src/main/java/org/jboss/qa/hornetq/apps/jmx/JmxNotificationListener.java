package org.jboss.qa.hornetq.apps.jmx;

import javax.management.Notification;
import javax.management.NotificationListener;
import java.util.List;

/**
 * Created by mnovak on 3/17/15.
 */
public interface JmxNotificationListener extends NotificationListener {

    @Override
    void handleNotification(Notification notification, Object handback);

    void waitForNotificationsCount(int notificationsCount, long timeout) throws InterruptedException;

    List<Notification> getCaughtNotifications();
}
