package org.jboss.qa.hornetq.apps.jmx;


import org.apache.log4j.Logger;

import javax.management.Notification;
import javax.management.NotificationListener;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;


/**
 * Simple JMX listener that will catch any notification on the MBean it's listening to.
 */
public class JmxNotificationListener implements NotificationListener {

    private static final Logger LOG = Logger.getLogger(JmxNotificationListener.class);

    private final List<Notification> caughtNotifications = new LinkedList<Notification>();

    @Override
    public void handleNotification(Notification notification, Object handback) {
        LOG.info("JMX notification (type " + notification.getType() + "): " + notification.getMessage());
        caughtNotifications.add(notification);
    }

    /**
     *
     * @param notificationsCount
     * @param timeout milliseconds
     * @throws InterruptedException
     */
    public void waitForNotificationsCount(int notificationsCount, long timeout) throws InterruptedException{
        long start=System.currentTimeMillis();
        long duration=0;

        while(getCaughtNotifications().size()<notificationsCount && duration<timeout){
            Thread.sleep(100);
            duration=System.currentTimeMillis()-start;
        }


    }

    public List<Notification> getCaughtNotifications() {
        return Collections.unmodifiableList(caughtNotifications);
    }

}
