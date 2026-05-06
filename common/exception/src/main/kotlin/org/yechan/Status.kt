package org.yechan

enum class Status {
    BAD_REQUEST,
    RESOURCE_NOT_FOUND,
    INTERNAL_SERVER_ERROR,
    AUTHENTICATION_FAILED,
    ;

    fun toHttpStatus(): Int = when (this) {
        BAD_REQUEST -> 400
        RESOURCE_NOT_FOUND -> 404
        AUTHENTICATION_FAILED -> 401
        INTERNAL_SERVER_ERROR -> 500
    }
}
