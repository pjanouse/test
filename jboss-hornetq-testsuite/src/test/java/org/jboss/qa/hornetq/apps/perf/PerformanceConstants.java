package org.jboss.qa.hornetq.apps.perf;

/**
 * Basic constants shared across MDBs and client in performance tests
 */
public interface PerformanceConstants {

    // When was message created
    String MESSAGE_PARAM_CREATED = "created";

    // When was message finished all its cycles
    String MESSAGE_PARAM_FINISHED = "finished";

    // Actual counter - counter for cycles
    String MESSAGE_PARAM_COUNTER = "counter";

    // Index of the message
    String MESSAGE_PARAM_INDEX = "index";

    // How many cycles for message
    String MESSAGE_PARAM_CYCLES = "cycles";

    // System configuration property for max wait time in test
    String MAX_WAIT_TIME_PARAM = "performance.wait";

}
