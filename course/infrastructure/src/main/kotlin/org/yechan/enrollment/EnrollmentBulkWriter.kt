package org.yechan.enrollment

interface EnrollmentBulkWriter {
    fun saveAllBulk(enrollments: List<EnrollmentModelData>)
}
