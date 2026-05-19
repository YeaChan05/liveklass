package org.yechan;

public enum Status {
    BAD_REQUEST,
    CONFLICT,
    RESOURCE_NOT_FOUND,
    INTERNAL_SERVER_ERROR,
    AUTHENTICATION_FAILED,
    ACCESS_DENIED,
    TOKEN_EXPIRED,
    FORBIDDEN;

    public int toHttpStatus() {
        return switch (this) {
            case BAD_REQUEST -> 400;
            case CONFLICT -> 409;
            case RESOURCE_NOT_FOUND -> 404;
            case AUTHENTICATION_FAILED, TOKEN_EXPIRED -> 401;
            case ACCESS_DENIED, FORBIDDEN -> 403;
            case INTERNAL_SERVER_ERROR -> 500;
        };
    }
}
