package org.yechan;

import java.time.LocalDateTime;

public record ApiSuccessResponse<T>(
    boolean success,
    LocalDateTime timestamp,
    T body
) implements ApiResponse {
}
