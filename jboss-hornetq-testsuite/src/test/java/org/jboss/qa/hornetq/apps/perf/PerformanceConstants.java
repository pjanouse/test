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

    // Type of received message
    String MESSAGE_TYPE = "message_type";

    // Size of the received message
    String MESSAGE_LENGTH = "message_length";

    // Count of the messages used in test
    String MESSAGES_COUNT_PARAM = "performance.messages";
    String LARGE_MESSAGES_COUNT_PARAM = "performance.large_messages";

    // How many cycles inside the container?
    String MESSAGES_CYCLES_PARAM = "performance.messages_cycles";
    String LARGE_MESSAGES_CYCLES_PARAM = "performance.large_messages_cycles";

    // Length of the used large messages
    String LARGE_MESSAGES_LENGTH = "performance.large_messages_length";
}
