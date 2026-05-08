package org.yechan.course

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
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
    ): CourseResponse = CourseResponse.from(courseUseCase.getCourse(courseId))

    @GetMapping("/courses")
    fun getCourses(): List<CourseResponse> = courseUseCase.getCourses().map(CourseResponse::from)

    @PostMapping("/courses")
    fun createCourse(
        @LoginUserId userId: Long,
        @RequestBody @Valid request: CreateCourseRequest,
    ): CourseResponse = CourseResponse.from(courseUseCase.createCourse(request.toCommand(userId)))

    @PostMapping("/courses/{courseId}/open")
    fun openCourse(
        @LoginUserId userId: Long,
        @PathVariable courseId: Long,
    ): CourseResponse = CourseResponse.from(courseUseCase.openCourse(CourseStatusCommand(userId, courseId)))

    @PostMapping("/courses/{courseId}/close")
    fun closeCourse(
        @LoginUserId userId: Long,
        @PathVariable courseId: Long,
    ): CourseResponse = CourseResponse.from(courseUseCase.closeCourse(CourseStatusCommand(userId, courseId)))
}
