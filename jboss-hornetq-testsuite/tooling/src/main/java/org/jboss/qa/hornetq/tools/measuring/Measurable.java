package org.jboss.qa.hornetq.tools.measuring;

import java.util.List;

/**
 * Created by mstyk on 4/12/16.
 */
public interface Measurable {

    List<String> measure();

    List<String> getHeaders();

}
