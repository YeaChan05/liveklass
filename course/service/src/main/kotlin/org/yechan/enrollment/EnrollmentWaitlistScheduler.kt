package org.yechan.enrollment

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional
import org.yechan.course.CourseRepository
import org.yechan.course.CourseStatus

open class EnrollmentWaitlistScheduler(
    private val waitlistRepository: EnrollmentWaitlistRepository,
    private val courseRepository: CourseRepository,
    private val courseBlukWriter: CourseBulkWriter,
    private val enrollmentBulkWriter: EnrollmentBulkWriter,
) {
    @Scheduled(fixedDelay = 5_000)
    @Transactional
    open fun processWaitlists() {
        val courseIds = waitlistRepository.findCourseIds().ifEmpty { return }

        val courses = courseRepository.findAllOpendCoursesByIds(courseIds)

        val promotions = courses.flatMap { course ->
            val courseId = course.courseId ?: return@flatMap emptyList()
            val promotableCount = course.seatLeftCount

            if (course.status != CourseStatus.OPEN || promotableCount <= 0) {
                return@flatMap emptyList()
            }

            (1..promotableCount).mapNotNull {
                val waitlist = waitlistRepository.pop(courseId) ?: return@mapNotNull null

                EnrollmentModelData(
                    courseId = courseId,
                    memberId = waitlist.memberId,
                )
            }
        }.ifEmpty { return }

        val reservedCountsByCourseId = promotions
            .groupingBy { it.courseId }
            .eachCount()

        courseBlukWriter.reserveSeatsBulk(reservedCountsByCourseId)

        val enrollments = promotions.map { promotion ->
            EnrollmentModelData(
                courseId = promotion.courseId,
                memberId = promotion.memberId,
            )
        }

        enrollmentBulkWriter.saveAllBulk(enrollments)
    }
}
