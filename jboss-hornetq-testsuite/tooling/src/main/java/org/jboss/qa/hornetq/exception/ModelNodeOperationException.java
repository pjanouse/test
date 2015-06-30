package org.jboss.qa.hornetq.exception;


public class ModelNodeOperationException extends Exception {

    public ModelNodeOperationException() {
        super();
    }

    public ModelNodeOperationException(String message) {
        super(message);
    }

    public ModelNodeOperationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ModelNodeOperationException(Throwable cause) {
        super(cause);
    }
}
