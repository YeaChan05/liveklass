package org.yechan.course

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.yechan.member.MemberModel
import org.yechan.member.MemberRepository
import org.yechan.member.MemberRole
import java.time.LocalDateTime

class CourseServiceTest {
    private val memberRepository = FakeMemberRepository()
    private val courseRepository = FakeCourseRepository()
    private val service = CourseService(memberRepository, courseRepository)

    @Test
    fun `크리에이터는 강의를 등록하고 모집을 시작하고 마감한다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))

        val created = service.createCourse(createCourseCommand(creatorId = 1L))
        val opened =
            service.openCourse(CourseStatusCommand(memberId = 1L, courseId = created.courseId))
        val closed =
            service.closeCourse(CourseStatusCommand(memberId = 1L, courseId = created.courseId))

        assertEquals(CourseStatus.DRAFT, created.status)
        assertEquals(CourseStatus.OPEN, opened.status)
        assertEquals(CourseStatus.CLOSED, closed.status)
    }

    private fun createCourseCommand(
        creatorId: Long,
        capacity: Int = 2,
    ) = CreateCourseCommand(
        creatorId = creatorId,
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
    ) = MemberModel(
        memberId = id,
        email = "user$id@example.com",
        passwordHash = "hash",
        name = "user$id",
        role = role,
    )
}

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
        val saved = if (course.courseId == null) course.copy(courseId = nextId++) else course
        courses[requireNotNull(saved.courseId)] = saved
        return saved
    }

    override fun findById(courseId: Long): CourseModel? = courses[courseId]

    override fun findAll(): List<CourseModel> = courses.values.toList()
}
