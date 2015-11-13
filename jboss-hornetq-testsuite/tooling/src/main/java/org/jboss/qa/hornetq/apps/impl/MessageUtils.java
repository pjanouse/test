package org.jboss.qa.hornetq.apps.impl;

import javax.jms.Message;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by mnovak on 11/12/15.
 */
public class MessageUtils {

    private static String dotReplacement = "DOT";

    public static Message setPropertiesToMessage(Map<String, String> properties, Message message) throws Exception {
        if (message != null) {
            for (String key : properties.keySet()) {
                String properKey = getProperkey(key);
                message.setStringProperty(properKey, properties.get(key));
            }
        }
        return message;
    }

    private static String getProperkey(String key) {
        return key.replaceAll("\\.", dotReplacement);
    }

    private static String getkey(String properKey) {
        return properKey.replaceAll(dotReplacement, ".");
    }

    public static Map<String, String> getPropertiesFromMessage(Message message) throws Exception {

        Map<String, String> properties = new HashMap<String, String>();
        Enumeration<String> messageProperties = message.getPropertyNames();
        while (messageProperties.hasMoreElements()) {
            String properKey = messageProperties.nextElement();
            String key = getkey(properKey);
            properties.put(key, message.getStringProperty(properKey));
        }
        return properties;
    }

}
