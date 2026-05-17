package org.yechan.enrollment

import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.time.Instant

@Component
class EnrollmentWaitlistSseHandler(
    private val enrollmentUseCase: EnrollmentUseCase,
) {
    fun getMyWaitlist(userId: Long): SseEmitter {
        val emitter = SseEmitter()
        val waitlist = enrollmentUseCase.getMyWaitlist(userId).map(WaitlistInfo::toResponse)

        try {
            emitter.send(
                SseEmitter.event()
                    .name("waitlist")
                    .data(waitlist),
            )
            emitter.complete()
        } catch (e: IOException) {
            emitter.completeWithError(e)
        }

        return emitter
    }
}

private fun WaitlistInfo.toResponse(): EnrollmentWaitlistResponse = EnrollmentWaitlistResponse(
    courseId = courseId,
    memberId = memberId,
    requestedAt = requestedAt,
)

data class EnrollmentWaitlistResponse(
    val courseId: Long,
    val memberId: Long,
    val requestedAt: Instant,
)
