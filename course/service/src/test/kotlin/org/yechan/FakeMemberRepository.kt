package org.yechan

import org.yechan.course.CourseModel
import org.yechan.course.CourseModelData
import org.yechan.course.CourseRepository
import org.yechan.course.CourseStatus
import org.yechan.enrollment.EnrollmentModel
import org.yechan.enrollment.EnrollmentModelData
import org.yechan.enrollment.EnrollmentRepository
import org.yechan.enrollment.EnrollmentWaitlistEntry
import org.yechan.enrollment.EnrollmentWaitlistRepository
import org.yechan.member.MemberModel
import org.yechan.member.MemberRepository
import java.time.Instant

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

class FakeCourseRepository : CourseRepository {
    private val courses = linkedMapOf<Long, CourseModel>()
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

    override fun reserveSeatIfAvailable(courseId: Long): Boolean {
        val course = courses[courseId] ?: return false

        if (course.status != CourseStatus.OPEN) {
            return false
        }

        if (course.seatLeftCount <= 0) {
            return false
        }

        courses[courseId] = CourseModelData(
            courseId = course.courseId,
            creatorId = course.creatorId,
            title = course.title,
            description = course.description,
            price = course.price,
            capacity = course.capacity,
            seatLeftCount = course.seatLeftCount - 1,
            periodStart = course.periodStart,
            periodEnd = course.periodEnd,
            status = course.status,
        )

        return true
    }

    override fun releaseSeatIfPossible(courseId: Long): Boolean {
        val course = courses[courseId] ?: return false

        if (course.seatLeftCount >= course.capacity) {
            return false
        }

        courses[courseId] = CourseModelData(
            courseId = course.courseId,
            creatorId = course.creatorId,
            title = course.title,
            description = course.description,
            price = course.price,
            capacity = course.capacity,
            seatLeftCount = course.seatLeftCount + 1,
            periodStart = course.periodStart,
            periodEnd = course.periodEnd,
            status = course.status,
        )

        return true
    }
}

class FakeEnrollmentRepository : EnrollmentRepository {
    private val enrollments = linkedMapOf<Long, EnrollmentModel>()
    private var nextId = 1L

    override fun save(
        enrollment: EnrollmentModel,
        courseId: Long,
    ): EnrollmentModel {
        val saved = if (enrollment.enrollmentId == null) {
            EnrollmentModelData(
                enrollmentId = nextId++,
                courseId = enrollment.courseId,
                memberId = enrollment.memberId,
                status = enrollment.status,
            )
        } else {
            enrollment
        }

        enrollments[requireNotNull(saved.enrollmentId)] = saved
        return saved
    }

    override fun findById(enrollmentId: Long): EnrollmentModel? = enrollments[enrollmentId]

    override fun findByMemberId(memberId: Long): List<EnrollmentModel> = enrollments.values.filter { it.memberId == memberId }
}

class FakeEnrollmentWaitlistRepository : EnrollmentWaitlistRepository {
    private val entries = linkedMapOf<Long, MutableList<EnrollmentWaitlistEntry>>()

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
    }

    override fun pop(courseId: Long): EnrollmentWaitlistEntry? {
        val queue = entries[courseId] ?: return null
        val first = queue.removeFirstOrNull()

        if (queue.isEmpty()) {
            entries.remove(courseId)
        }

        return first
    }

    override fun remove(
        courseId: Long,
        memberId: Long,
    ) {
        entries[courseId]?.removeIf { it.memberId == memberId }

        if (entries[courseId].isNullOrEmpty()) {
            entries.remove(courseId)
        }
    }

    override fun findCourseIds(): Set<Long> = entries.keys
}
