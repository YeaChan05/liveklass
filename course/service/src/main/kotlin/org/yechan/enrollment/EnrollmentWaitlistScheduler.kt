package org.yechan.enrollment

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional
import org.yechan.course.CourseRepository
import org.yechan.course.CourseStatus

open class EnrollmentWaitlistScheduler(
    private val waitlistRepository: EnrollmentWaitlistRepository,
    private val courseRepository: CourseRepository,
    private val enrollmentRepository: EnrollmentRepository,
) {
    @Scheduled(fixedDelay = 5_000)
    @Transactional
    open fun processWaitlists() {
        waitlistRepository.findCourseIds().forEach { courseId ->
            processWaitlist(courseId)
        }
    }

    private fun processWaitlist(courseId: Long) {
        var course = courseRepository.findById(courseId) ?: return
        while (course.status == CourseStatus.OPEN && course.seatLeftCount > 0) {
            val waitlistEntry = waitlistRepository.pop(courseId) ?: return
            course = courseRepository.save(course.reserveSeat())
            val enrollment = course.requestEnrollment(waitlistEntry.memberId)
            enrollmentRepository.save(enrollment, courseId)
        }
    }
}
