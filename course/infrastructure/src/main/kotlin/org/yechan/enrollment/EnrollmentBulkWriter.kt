package org.yechan.enrollment

import java.time.LocalDateTime

interface EnrollmentBulkWriter {
    fun saveAllBulk(enrollments: List<EnrollmentModelData>)

    fun updateAllExpired(courseIds: Collection<Long>, now: LocalDateTime): Map<Long, Int>
}
