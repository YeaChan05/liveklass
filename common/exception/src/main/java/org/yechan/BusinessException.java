package org.yechan;

public class BusinessException extends RuntimeException {
    private final Status status;

    public BusinessException(Status status, String message) {
        super(message);
        this.status = status;
    }

    public BusinessException(String message) {
        super(message);
        this.status = Status.INTERNAL_SERVER_ERROR;
    }

    public Status getStatus() {
        return status;
    }

    public int getHttpStatus() {
        return status.toHttpStatus();
    }
}
