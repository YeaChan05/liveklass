package org.yechan

enum class Status {
    BAD_REQUEST,
    CONFLICT,
    RESOURCE_NOT_FOUND,
    INTERNAL_SERVER_ERROR,
    AUTHENTICATION_FAILED,
    ACCESS_DENIED,
    TOKEN_EXPIRED,
    FORBIDDEN,
    ;

    fun toHttpStatus(): Int = when (this) {
        BAD_REQUEST -> 400
        CONFLICT -> 409
        RESOURCE_NOT_FOUND -> 404
        AUTHENTICATION_FAILED -> 401
        ACCESS_DENIED -> 403
        TOKEN_EXPIRED -> 401
        INTERNAL_SERVER_ERROR -> 500
        FORBIDDEN -> 403
    }
}
