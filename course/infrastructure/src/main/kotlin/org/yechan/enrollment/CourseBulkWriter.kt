package org.yechan.enrollment

interface CourseBulkWriter {
    fun reserveSeatsBulk(courseIds: Map<Long, Int>)
}
