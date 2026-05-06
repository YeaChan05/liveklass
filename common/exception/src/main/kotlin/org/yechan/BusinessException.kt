package org.yechan

open class BusinessException : RuntimeException {
    val status: Status

    constructor(
        status: Status,
        message: String,
    ) : super(message) {
        this.status = status
    }

    constructor(message: String) : super(message) {
        status = Status.INTERNAL_SERVER_ERROR
    }

    fun getHttpStatus() = status.toHttpStatus()
}
