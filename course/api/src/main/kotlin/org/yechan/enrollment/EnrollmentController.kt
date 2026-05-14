package org.yechan.enrollment

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.yechan.LoginUserId

@RestController
@RequestMapping("/api", version = "v1")
class EnrollmentController(
    private val enrollmentUseCase: EnrollmentUseCase,
) {
    @PostMapping("/courses/{courseId}/enrollments")
    fun enroll(
        @LoginUserId userId: Long,
        @PathVariable courseId: Long,
    ): EnrollmentResponse = enrollmentUseCase.enroll(EnrollCourseCommand(userId, courseId)).toResponse()

    @PostMapping("/enrollments/{enrollmentId}/confirm")
    fun confirmEnrollment(
        @LoginUserId userId: Long,
        @PathVariable enrollmentId: Long,
    ): EnrollmentResponse = enrollmentUseCase.confirmEnrollment(EnrollmentStatusCommand(userId, enrollmentId))
        .toResponse()

    @PostMapping("/enrollments/{enrollmentId}/cancel")
    fun cancelEnrollment(
        @LoginUserId userId: Long,
        @PathVariable enrollmentId: Long,
    ): EnrollmentResponse = enrollmentUseCase.cancelEnrollment(EnrollmentStatusCommand(userId, enrollmentId))
        .toResponse()

    @GetMapping("/enrollments/me")
    fun getMyEnrollments(
        @LoginUserId userId: Long,
    ): List<EnrollmentResponse> = enrollmentUseCase.getMyEnrollments(userId).map(EnrollmentResult::toResponse)
}

private fun EnrollmentResult.toResponse(): EnrollmentResponse = EnrollmentResponse(
    enrollmentId = enrollmentId,
    courseId = courseId,
    memberId = memberId,
    status = status.toResponseStatus(),
)

private fun EnrollmentEnrollResult.toResponse(): EnrollmentResponse = when (this) {
    is EnrollmentEnrollResult.Enrolled -> enrollment.toResponse()

    is EnrollmentEnrollResult.Waitlisted -> EnrollmentResponse(
        enrollmentId = null,
        courseId = courseId,
        memberId = memberId,
        status = EnrollmentResponseStatus.WAITLISTED,
    )
}

private fun EnrollmentStatus.toResponseStatus(): EnrollmentResponseStatus = EnrollmentResponseStatus.valueOf(name)
