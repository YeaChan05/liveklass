package org.yechan.enrollment

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.yechan.course.CourseModel
import org.yechan.course.CourseModelData
import org.yechan.course.CourseRepository
import org.yechan.course.CourseService
import org.yechan.course.CourseStatusCommand
import org.yechan.course.CreateCourseCommand
import org.yechan.course.Money
import org.yechan.member.MemberModel
import org.yechan.member.MemberModelData
import org.yechan.member.MemberRepository
import org.yechan.member.MemberRole
import java.time.Instant
import java.time.LocalDateTime

class EnrollmentWaitlistSchedulerTest {
    private val memberRepository = FakeMemberRepository()
    private val courseRepository = FakeCourseRepository()
    private val enrollmentRepository = FakeEnrollmentRepository()
    private val waitlistRepository = FakeEnrollmentWaitlistRepository()
    private val courseService = CourseService(memberRepository, courseRepository)
    private val scheduler = EnrollmentWaitlistScheduler(
        waitlistRepository,
        courseRepository,
        enrollmentRepository,
    )

    @Test
    fun `스케줄러는 좌석이 생기면 대기열의 가장 오래된 회원부터 예약한다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        memberRepository.save(member(id = 3L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(capacity = 1), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))

        waitlistRepository.enqueue(course.courseId!!, 2L, Instant.parse("2026-01-01T00:00:00Z"))
        waitlistRepository.enqueue(course.courseId!!, 3L, Instant.parse("2026-01-01T00:00:01Z"))

        scheduler.processWaitlists()

        val changedCourse = courseService.getCourse(course.courseId)
        val enrollments = enrollmentRepository.findByMemberId(2L)

        assertThat(changedCourse.seatLeftCount).isEqualTo(0)
        assertThat(enrollments).hasSize(1)
        assertThat(waitlistRepository.findCourseIds()).containsExactly(course.courseId!!)
        assertThat(waitlistRepository.pop(course.courseId!!)?.memberId).isEqualTo(3L)
    }

    private fun createCourseCommand(
        capacity: Int = 2,
    ) = CreateCourseCommand(
        title = "Kotlin Basic",
        description = "Kotlin course",
        price = Money(100_000L),
        capacity = capacity,
        periodStart = LocalDateTime.of(2026, 6, 1, 0, 0),
        periodEnd = LocalDateTime.of(2026, 6, 30, 0, 0),
    )

    private fun member(
        id: Long,
        role: MemberRole,
    ) = MemberModelData(
        memberId = id,
        email = "user$id@example.com",
        passwordHash = "hash",
        name = "user$id",
        role = role,
    )

    private class FakeMemberRepository : MemberRepository {
        private val members = linkedMapOf<Long, MemberModel>()

        override fun save(member: MemberModel): MemberModel {
            members[requireNotNull(member.memberId)] = member
            return member
        }

        override fun existsByEmail(email: String): Boolean = members.values.any { it.email == email }

        override fun findByEmail(email: String): MemberModel? = members.values.firstOrNull { it.email == email }

        override fun findById(id: Long): MemberModel? = members[id]
    }

    private class FakeCourseRepository : CourseRepository {
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

        override fun reserveSeatIfAvailable(courseId: Long): Boolean = courses[courseId]?.let { it.seatLeftCount > 0 } ?: false

        override fun releaseSeatIfPossible(courseId: Long): Boolean = courses[courseId]?.let { it.seatLeftCount < it.capacity } ?: false
    }

    private class FakeEnrollmentRepository : EnrollmentRepository {
        private val enrollments = mutableListOf<EnrollmentModel>()
        private var nextId = 1L

        override fun save(
            enrollment: EnrollmentModel,
            courseId: Long,
        ): EnrollmentModel {
            val saved = if (enrollment.enrollmentId == null) {
                EnrollmentModelData(
                    enrollmentId = nextId++,
                    courseId = courseId,
                    memberId = enrollment.memberId,
                    status = enrollment.status,
                )
            } else {
                enrollment
            }
            enrollments += saved
            return saved
        }

        override fun findById(enrollmentId: Long): EnrollmentModel? = enrollments.firstOrNull { it.enrollmentId == enrollmentId }

        override fun findByMemberId(memberId: Long): List<EnrollmentModel> = enrollments.filter { it.memberId == memberId }
    }

    private class FakeEnrollmentWaitlistRepository : EnrollmentWaitlistRepository {
        private val entries = linkedMapOf<Long, MutableList<EnrollmentWaitlistEntry>>()

        override fun enqueue(
            courseId: Long,
            memberId: Long,
            requestedAt: Instant,
        ) {
            entries.getOrPut(courseId) { mutableListOf() } += EnrollmentWaitlistEntry(
                courseId,
                memberId,
                requestedAt,
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
}
