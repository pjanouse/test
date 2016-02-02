package org.jboss.qa.hornetq.tools;

import org.jboss.arquillian.config.descriptor.api.ContainerDef;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * Keeps initial configuration of containerProperties and provides unmodified containerProperties.
 * This prevents duplicate adding of JVM arguments
 *
 * Created by mstyk on 2/2/16.
 */
public class DefaultContainerConfigurationUtil {

    private static Map<Integer, Map<String, String>> originalContainerProperties = new HashMap<Integer, Map<String, String>>(4);

    public static Map<String, String> getOriginalContainerProperties(ContainerDef containerDef, int containerIndex) {

        if(originalContainerProperties.get(containerIndex) == null){
            originalContainerProperties.put(containerIndex, copyContainerProperties(containerDef.getContainerProperties()));
        }
        
        return copyContainerProperties(originalContainerProperties.get(containerIndex));
    }

    private static Map<String, String> copyContainerProperties(Map<String, String> containerProperties) {

        Map<String, String> properties = new HashMap<String, String>();

        for (String key : containerProperties.keySet()) {
            properties.put(key, containerProperties.get(key));
        }
        return properties;
    }
}
