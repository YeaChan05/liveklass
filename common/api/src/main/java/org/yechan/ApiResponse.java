package org.yechan;

import java.time.LocalDateTime;

public sealed interface ApiResponse permits ApiSuccessResponse, ApiErrorResponse {
    boolean success();

    LocalDateTime timestamp();
}
