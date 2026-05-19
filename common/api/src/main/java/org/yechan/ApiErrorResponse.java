package org.yechan;

import java.time.LocalDateTime;

public record ApiErrorResponse(
    boolean success,
    LocalDateTime timestamp,
    String message
) implements ApiResponse {
}
