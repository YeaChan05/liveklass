package org.yechan.enrollment

import java.time.LocalDateTime

interface EnrollmentExpirationProcessor {
    fun expireAll(
        courseIds: Collection<Long>,
        now: LocalDateTime,
    ): Map<Long, Int>
}

open class EnrollmentExpirationService(
    private val enrollmentBulkWriter: EnrollmentBulkWriter,
    private val courseBulkWriter: CourseBulkWriter,
) : EnrollmentExpirationProcessor {
    override fun expireAll(
        courseIds: Collection<Long>,
        now: LocalDateTime,
    ): Map<Long, Int> {
        // 1. enrollment 만료 대상을 EXPIRED로 전환
        val countsByCourseId =
            enrollmentBulkWriter.updateAllExpired(courseIds = courseIds, now = now)

        // 2. 각 course의 EXPIRED 수량만큼 좌석을 반환한다
        courseBulkWriter.releaseSeatsBulk(countsByCourseId)

        return countsByCourseId
    }
}
