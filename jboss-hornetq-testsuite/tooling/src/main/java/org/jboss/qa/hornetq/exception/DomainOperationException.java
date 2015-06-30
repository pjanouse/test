package org.jboss.qa.hornetq.exception;


public class DomainOperationException extends Exception {

    public DomainOperationException() {
        super();
    }

    public DomainOperationException(final String msg) {
        super(msg);
    }

    public DomainOperationException(Throwable cause) {
        super(cause);
    }

    public DomainOperationException(String message, Throwable cause) {
        super(message, cause);
    }

}
