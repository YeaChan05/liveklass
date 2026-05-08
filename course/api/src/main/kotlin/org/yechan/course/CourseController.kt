package org.yechan.course

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.yechan.LoginUserId

@RestController
@RequestMapping("/api", version = "v1")
class CourseController(
    private val courseUseCase: CourseUseCase,
) {
    @GetMapping("/courses/{courseId}")
    fun getCourse(
        @PathVariable courseId: Long,
    ): CourseResponse = courseUseCase.getCourse(courseId).toResponse()

    @GetMapping("/courses")
    fun getCourses(
        @RequestParam(required = false) status: CourseStatus? = null,
    ): List<CourseResponse> = courseUseCase.getCourses(status).map(CourseResult::toResponse)

    @PostMapping("/courses")
    fun createCourse(
        @LoginUserId userId: Long,
        @RequestBody @Valid request: CreateCourseRequest,
    ): CourseResponse = courseUseCase.createCourse(creatorId = userId, command = request.toCommand()).toResponse()

    @PostMapping("/courses/{courseId}/open")
    fun openCourse(
        @LoginUserId userId: Long,
        @PathVariable courseId: Long,
    ): CourseResponse = courseUseCase.openCourse(CourseStatusCommand(userId, courseId)).toResponse()

    @PostMapping("/courses/{courseId}/close")
    fun closeCourse(
        @LoginUserId userId: Long,
        @PathVariable courseId: Long,
    ): CourseResponse = courseUseCase.closeCourse(CourseStatusCommand(userId, courseId)).toResponse()
}

private fun CourseResult.toResponse(): CourseResponse = CourseResponse(
    courseId = courseId,
    title = title,
    description = description,
    price = price.amount,
    capacity = capacity,
    seatLeftCount = seatLeftCount,
    currentEnrollmentCount = currentEnrollmentCount,
    periodStart = periodStart,
    periodEnd = periodEnd,
    status = status,
)
