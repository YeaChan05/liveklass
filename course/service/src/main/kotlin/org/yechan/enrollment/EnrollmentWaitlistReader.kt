package org.yechan.enrollment

interface EnrollmentWaitlistReader {
    fun isWaitlistMode(courseId: Long): Boolean

    fun findByMemberId(memberId: Long): List<EnrollmentWaitlistEntry>

    fun findCourseIds(): Set<Long>
}

class EnrollmentWaitlistRepositoryReader(
    private val waitlistRepository: EnrollmentWaitlistRepository,
) : EnrollmentWaitlistReader {
    override fun isWaitlistMode(courseId: Long): Boolean = waitlistRepository.isSoldOut(courseId)

    override fun findByMemberId(memberId: Long): List<EnrollmentWaitlistEntry> = waitlistRepository.findByMemberId(memberId)

    override fun findCourseIds(): Set<Long> = waitlistRepository.findCourseIds()
}
