package org.jboss.qa.hornetq.apps.impl.verifiers.configurable;

import java.util.List;
import java.util.Map;

/**
 * Created by mstyk on 6/28/16.
 */
public interface Verifiable {
    String getTitle();

    boolean isOk();

    boolean verify();

    List<Map<String, String>> getProblemMessages();

    List<String> getProblemMessagesIds();
}
