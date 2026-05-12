package org.yechan

import java.time.LocalDateTime

sealed interface ApiResponse {
    val success: Boolean
    val timestamp: LocalDateTime
}

data class ApiSuccessResponse<T>(
    override val success: Boolean = true,
    override val timestamp: LocalDateTime = LocalDateTime.now(),
    val body: T,
) : ApiResponse

data class ApiErrorResponse(
    override val success: Boolean = false,
    override val timestamp: LocalDateTime = LocalDateTime.now(),
    val message: String,
) : ApiResponse
