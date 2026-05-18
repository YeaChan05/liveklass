package org.yechan.enrollment

import org.springframework.scheduling.annotation.Scheduled
import org.yechan.course.CourseReader

interface WaitlistAssignmentRecoveryUseCase {
    fun recoverAssignments()
}

open class EnrollmentWaitlistScheduler(
    private val waitlistAssignmentRecoveryService: WaitlistAssignmentRecoveryUseCase,
) {
    @Scheduled(fixedDelay = 5_000)
    fun processWaitlists() {
        waitlistAssignmentRecoveryService.recoverAssignments()
    }
}

class WaitlistAssignmentRecoveryService(
    private val waitlistReader: EnrollmentWaitlistReader,
    private val courseReader: CourseReader,
    private val waitlistWriter: EnrollmentWaitlistWriter,
) : WaitlistAssignmentRecoveryUseCase {
    override fun recoverAssignments() {
        val courseIds = waitlistReader.findCourseIds().ifEmpty { return }

        val courses = courseReader.getOpenedCoursesByIds(courseIds)
        courses.forEach { course ->
            val courseId = course.courseId
            val assignableCount = course.seatLeftCount

            if (assignableCount <= 0) {
                return@forEach
            }

            waitlistWriter.assignAfterSeatRelease(courseId, assignableCount)
        }
    }
}
