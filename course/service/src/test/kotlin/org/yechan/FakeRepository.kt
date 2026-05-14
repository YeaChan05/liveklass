package org.yechan

import org.yechan.course.CourseInvalidStateException
import org.yechan.course.CourseModel
import org.yechan.course.CourseModelData
import org.yechan.course.CourseRepository
import org.yechan.course.CourseStatus
import org.yechan.enrollment.CourseBulkWriter
import org.yechan.enrollment.EnrollmentBulkWriter
import org.yechan.enrollment.EnrollmentExpirationTarget
import org.yechan.enrollment.EnrollmentModel
import org.yechan.enrollment.EnrollmentModelData
import org.yechan.enrollment.EnrollmentRepository
import org.yechan.enrollment.EnrollmentStatus
import org.yechan.enrollment.EnrollmentWaitlistEntry
import org.yechan.enrollment.EnrollmentWaitlistRepository
import org.yechan.member.MemberModel
import org.yechan.member.MemberRepository
import java.time.Instant
import java.time.LocalDateTime

class FakeMemberRepository : MemberRepository {
    private val members = linkedMapOf<Long, MemberModel>()

    override fun save(member: MemberModel): MemberModel {
        members[requireNotNull(member.memberId)] = member
        return member
    }

    override fun existsByEmail(email: String): Boolean = members.values.any { it.email == email }

    override fun findByEmail(email: String): MemberModel? = members.values.firstOrNull { it.email == email }

    override fun findById(id: Long): MemberModel? = members[id]
}

class FakeCourseRepository :
    CourseRepository,
    CourseBulkWriter {
    val courses = linkedMapOf<Long, CourseModel>()
    private var nextId = 1L

    override fun save(course: CourseModel): CourseModel {
        val saved = if (course.courseId == null) {
            CourseModelData(
                courseId = nextId++,
                creatorId = course.creatorId,
                title = course.title,
                description = course.description,
                price = course.price,
                capacity = course.capacity,
                seatLeftCount = course.seatLeftCount,
                periodStart = course.periodStart,
                periodEnd = course.periodEnd,
                status = course.status,
            )
        } else {
            course
        }

        courses[requireNotNull(saved.courseId)] = saved
        return saved
    }

    override fun findById(courseId: Long): CourseModel? = courses[courseId]

    override fun findAll(): List<CourseModel> = courses.values.toList()

    override fun findAllByStatus(status: CourseStatus): List<CourseModel> = courses.values.filter { it.status == status }

    override fun findAllOpenedCoursesByIds(courseIds: Collection<Long>): List<CourseModel> = courses.values.filter {
        it.courseId in courseIds && it.status == CourseStatus.OPEN
    }

    override fun reserveSeatIfAvailable(courseId: Long): Boolean {
        val course = courses[courseId] ?: return false

        if (course.status != CourseStatus.OPEN) {
            return false
        }

        if (course.seatLeftCount <= 0) {
            return false
        }

        courses[courseId] = course.copyWithSeatLeftCount(
            seatLeftCount = course.seatLeftCount - 1,
        )

        return true
    }

    override fun reserveSeatsBulk(courseIds: Map<Long, Int>) {
        courseIds.forEach { (courseId, count) ->
            if (count <= 0) {
                return@forEach
            }

            val course = courses[courseId] ?: return@forEach

            if (course.status != CourseStatus.OPEN) {
                return@forEach
            }

            if (course.seatLeftCount < count) {
                return@forEach
            }

            courses[courseId] = course.copyWithSeatLeftCount(
                seatLeftCount = course.seatLeftCount - count,
            )
        }
    }

    override fun releaseSeatIfPossible(courseId: Long): Boolean {
        val course = courses[courseId] ?: return false

        if (course.seatLeftCount >= course.capacity) {
            return false
        }

        courses[courseId] = course.copyWithSeatLeftCount(
            seatLeftCount = course.seatLeftCount + 1,
        )

        return true
    }

    private fun CourseModel.copyWithSeatLeftCount(
        seatLeftCount: Int,
    ): CourseModel = CourseModelData(
        courseId = courseId,
        creatorId = creatorId,
        title = title,
        description = description,
        price = price,
        capacity = capacity,
        seatLeftCount = seatLeftCount,
        periodStart = periodStart,
        periodEnd = periodEnd,
        status = status,
    )

    override fun releaseSeatsBulk(courseIds: Map<Long, Int>) {
        val targets = courseIds.filterValues { it > 0 }
        if (targets.isEmpty()) {
            return
        }

        targets.forEach { (courseId, count) ->
            val course = courses[courseId]
                ?: throw CourseInvalidStateException("좌석을 반환할 수 없습니다.")

            if (course.seatLeftCount + count > course.capacity) {
                throw CourseInvalidStateException("좌석을 반환할 수 없습니다.")
            }
        }

        targets.forEach { (courseId, count) ->
            val course = courses[courseId] ?: return@forEach
            courses[courseId] = course.copyWithSeatLeftCount(
                seatLeftCount = course.seatLeftCount + count,
            )
        }
    }
}

class FakeEnrollmentRepository :
    EnrollmentRepository,
    EnrollmentBulkWriter {
    val enrollments = linkedMapOf<Long, EnrollmentModel>()
    private var nextId = 1L

    override fun save(
        enrollment: EnrollmentModel,
    ): EnrollmentModel {
        val saved = if (enrollment.enrollmentId == null) {
            enrollment.copyWithId(
                enrollmentId = nextId++,
                courseId = enrollment.courseId,
            )
        } else {
            enrollment
        }

        enrollments[requireNotNull(saved.enrollmentId)] = saved
        return saved
    }

    override fun saveAllBulk(enrollments: List<EnrollmentModelData>) {
        enrollments.forEach { enrollment ->
            save(
                enrollment = enrollment,
            )
        }
    }

    override fun findById(enrollmentId: Long): EnrollmentModel? = enrollments[enrollmentId]

    override fun findByMemberId(memberId: Long): List<EnrollmentModel> = enrollments.values.filter { it.memberId == memberId }

    override fun findExpiredPaymentPendingTargets(
        now: LocalDateTime,
        limit: Int,
    ): List<EnrollmentExpirationTarget> = enrollments.values
        .asSequence()
        .filter { it.status == EnrollmentStatus.PENDING }
        .filter { it.paymentPendingExpiresAt <= now }
        .sortedBy { it.paymentPendingExpiresAt }
        .map {
            EnrollmentExpirationTarget(
                enrollmentId = requireNotNull(it.enrollmentId),
                courseId = it.courseId,
            )
        }
        .take(limit)
        .toList()

    override fun expirePaymentPendingIfExpired(
        enrollmentId: Long,
        now: LocalDateTime,
    ): Boolean {
        val enrollment = enrollments[enrollmentId] ?: return false

        if (enrollment.status != EnrollmentStatus.PENDING) {
            return false
        }

        if (enrollment.paymentPendingExpiresAt > now) {
            return false
        }

        enrollments[enrollmentId] = enrollment.copyWithStatus(
            status = EnrollmentStatus.EXPIRED,
        )

        return true
    }

    private fun EnrollmentModel.copyWithId(
        enrollmentId: Long,
        courseId: Long,
    ): EnrollmentModel = EnrollmentModelData(
        enrollmentId = enrollmentId,
        courseId = courseId,
        memberId = memberId,
        status = status,
        paymentPendingStartedAt = paymentPendingStartedAt,
        paymentPendingExpiresAt = paymentPendingExpiresAt,
    )

    private fun EnrollmentModel.copyWithStatus(
        status: EnrollmentStatus,
    ): EnrollmentModel = EnrollmentModelData(
        enrollmentId = enrollmentId,
        courseId = courseId,
        memberId = memberId,
        status = status,
        paymentPendingStartedAt = paymentPendingStartedAt,
        paymentPendingExpiresAt = paymentPendingExpiresAt,
    )

    override fun updateAllExpired(
        courseIds: Collection<Long>,
        now: LocalDateTime,
    ): Map<Long, Int> {
        val targets = enrollments.values
            .filter { it.status == EnrollmentStatus.PENDING }
            .filter { it.paymentPendingExpiresAt <= now }
            .filter { it.courseId in courseIds }

        targets.forEach { enrollment ->
            enrollments[requireNotNull(enrollment.enrollmentId)] = enrollment.copyWithStatus(
                status = EnrollmentStatus.EXPIRED,
            )
        }

        return targets
            .groupBy { it.courseId }
            .mapValues { (_, enrollments) -> enrollments.size }
    }
}

class FakeEnrollmentWaitlistRepository : EnrollmentWaitlistRepository {
    private val entries = linkedMapOf<Long, MutableList<EnrollmentWaitlistEntry>>()
    private val soldOutCourseIds = mutableSetOf<Long>()

    override fun enqueue(
        courseId: Long,
        memberId: Long,
        requestedAt: Instant,
    ) {
        entries.getOrPut(courseId) { mutableListOf() } += EnrollmentWaitlistEntry(
            courseId = courseId,
            memberId = memberId,
            requestedAt = requestedAt,
        )

        entries[courseId]?.sortBy { it.requestedAt }
    }

    override fun pop(courseId: Long): EnrollmentWaitlistEntry? {
        val queue = entries[courseId] ?: return null
        val first = queue.removeFirstOrNull()

        if (queue.isEmpty()) {
            entries.remove(courseId)
            soldOutCourseIds -= courseId
        }

        return first
    }

    override fun findByMemberId(memberId: Long): List<EnrollmentWaitlistEntry> = entries.values
        .flatten()
        .filter { it.memberId == memberId }
        .sortedBy { it.requestedAt }

    override fun remove(
        courseId: Long,
        memberId: Long,
    ) {
        entries[courseId]?.removeIf { it.memberId == memberId }

        if (entries[courseId].isNullOrEmpty()) {
            entries.remove(courseId)
            soldOutCourseIds -= courseId
        }
    }

    override fun findCourseIds(): Set<Long> = entries.keys.toSet()

    override fun isSoldOut(courseId: Long): Boolean = courseId in soldOutCourseIds

    override fun markSoldOut(courseId: Long) {
        soldOutCourseIds += courseId
    }

    override fun clearSoldOut(courseId: Long) {
        soldOutCourseIds -= courseId
    }
}
