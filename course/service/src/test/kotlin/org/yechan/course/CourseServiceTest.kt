package org.yechan.course

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.yechan.FakeCourseRepository
import org.yechan.FakeMemberRepository
import org.yechan.member.MemberModelData
import org.yechan.member.MemberNotFoundException
import org.yechan.member.MemberRole
import java.time.LocalDateTime

class CourseServiceTest {

    @Test
    fun `조회 요청은 조회 처리기에 맡긴다`() {
        // Arrange
        val queryProcessor = RecordingCourseQueryHandler()
        val service = CourseService(queryProcessor, RecordingCourseCommandHandler())

        // Act
        val course = service.getCourse(10L)
        val courses = service.getCourses(CourseStatus.OPEN)

        // Assert
        assertThat(course.courseId).isEqualTo(10L)
        assertThat(courses).hasSize(1)
        assertThat(queryProcessor.requestedCourseIds).containsExactly(10L)
        assertThat(queryProcessor.requestedStatuses).containsExactly(CourseStatus.OPEN)
    }

    @Test
    fun `변경 요청은 명령 처리기에 맡긴다`() {
        // Arrange
        val commandProcessor = RecordingCourseCommandHandler()
        val service = CourseService(RecordingCourseQueryHandler(), commandProcessor)
        val createCommand = createCourseCommand()
        val statusCommand = CourseStatusCommand(memberId = 1L, courseId = 10L)

        // Act
        service.createCourse(createCommand, 1L)
        service.openCourse(statusCommand)
        service.closeCourse(statusCommand)

        // Assert
        assertThat(commandProcessor.createdCommands).containsExactly(createCommand to 1L)
        assertThat(commandProcessor.openedCommands).containsExactly(statusCommand)
        assertThat(commandProcessor.closedCommands).containsExactly(statusCommand)
    }
    private val memberRepository = FakeMemberRepository()
    private val courseRepository = FakeCourseRepository()
    private val service = CourseService(
        CourseQueryProcessor(courseRepository),
        CourseCommandProcessor(memberRepository, courseRepository),
    )

    @Test
    fun `CREATOR는 강의를 등록하고 모집을 시작하고 마감한다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))

        val created = service.createCourse(createCourseCommand(), 1L)
        val opened =
            service.openCourse(CourseStatusCommand(memberId = 1L, courseId = created.courseId))
        val closed =
            service.closeCourse(CourseStatusCommand(memberId = 1L, courseId = created.courseId))

        assertThat(created.courseId).isNotNull()
        assertThat(opened.courseId).isNotNull()
        assertThat(closed.courseId).isNotNull()

        assertThat(created.status).isEqualTo(CourseStatus.DRAFT)
        assertThat(opened.status).isEqualTo(CourseStatus.OPEN)
        assertThat(closed.status).isEqualTo(CourseStatus.CLOSED)
    }

    @Test
    fun `상태 조건이 없으면 모든 강의를 조회한다`() {
        // Arrange
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))

        service.createCourse(createCourseCommand(), 1L)

        val openedCourse = service.createCourse(
            createCourseCommand(),
            1L,
        )

        service.openCourse(
            CourseStatusCommand(
                memberId = 1L,
                courseId = openedCourse.courseId,
            ),
        )

        // Act
        val results = service.getCourses()

        // Assert
        assertThat(results).hasSize(2)
        assertThat(results.map { it.status })
            .containsExactlyInAnyOrder(
                CourseStatus.DRAFT,
                CourseStatus.OPEN,
            )
    }

    @Test
    fun `상태 조건에 해당하는 강의만 조회한다`() {
        // Arrange
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))

        service.createCourse(createCourseCommand(), 1L)

        val openedCourse = service.createCourse(
            createCourseCommand(),
            1L,
        )

        service.openCourse(
            CourseStatusCommand(
                memberId = 1L,
                courseId = openedCourse.courseId,
            ),
        )

        // Act
        val results = service.getCourses(CourseStatus.OPEN)

        // Assert
        assertThat(results).hasSize(1)
        assertThat(results.first().status)
            .isEqualTo(CourseStatus.OPEN)
    }

    @Test
    fun `상태 조건에 해당하는 강의가 없으면 빈 목록을 반환한다`() {
        // Arrange
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))

        service.createCourse(createCourseCommand(), 1L)

        // Act
        val results = service.getCourses(CourseStatus.CLOSED)

        // Assert
        assertThat(results).isEmpty()
    }

    @Test
    fun `존재하지 않는 강의는 조회할 수 없다`() {
        // Arrange

        // ACT & Assert
        assertThatThrownBy {
            service.getCourse(999L)
        }
            .isInstanceOf(CourseNotFoundException::class.java)
    }

    @Test
    fun `존재하지 않는 회원은 강의를 생성할 수 없다`() {
        // Arrange
        val command = createCourseCommand()

        // ACT & Assert
        assertThatThrownBy {
            service.createCourse(command, 999L)
        }
            .isInstanceOf(MemberNotFoundException::class.java)
    }

    @Test
    fun `강의 생성 시 생성자의 아이디로 저장된다`() {
        // Arrange
        memberRepository.save(
            member(
                id = 1L,
                role = MemberRole.CREATOR,
            ),
        )

        val command = createCourseCommand()

        // Act
        val result = service.createCourse(command, 1L)

        // Assert
        assertThat(result.creatorId).isEqualTo(1L)
        assertThat(result.title).isEqualTo(command.title)
        assertThat(result.description).isEqualTo(command.description)
    }

    @Test
    fun `강의 소유자는 모집을 시작할 수 있다`() {
        // Arrange
        memberRepository.save(
            member(
                id = 1L,
                role = MemberRole.CREATOR,
            ),
        )

        val created = service.createCourse(
            createCourseCommand(),
            1L,
        )

        // Act
        val result = service.openCourse(
            CourseStatusCommand(
                memberId = 1L,
                courseId = created.courseId,
            ),
        )

        // Assert
        assertThat(result.status).isEqualTo(CourseStatus.OPEN)
    }

    @Test
    fun `강의 소유자가 아닌 회원은 모집을 시작할 수 없다`() {
        // Arrange
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CREATOR))

        val created = service.createCourse(
            createCourseCommand(),
            1L,
        )

        // ACT & Assert
        assertThatThrownBy {
            service.openCourse(
                CourseStatusCommand(
                    memberId = 2L,
                    courseId = created.courseId,
                ),
            )
        }
            .isInstanceOf(CourseAccessDeniedException::class.java)
    }

    @Test
    fun `존재하지 않는 강의는 모집을 시작할 수 없다`() {
        // Arrange
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))

        // ACT & Assert
        assertThatThrownBy {
            service.openCourse(
                CourseStatusCommand(
                    memberId = 1L,
                    courseId = 999L,
                ),
            )
        }
            .isInstanceOf(CourseNotFoundException::class.java)
    }

    @Test
    fun `존재하지 않는 회원은 모집을 시작할 수 없다`() {
        // Arrange
        // do nothing

        // ACT & Assert
        assertThatThrownBy {
            service.openCourse(
                CourseStatusCommand(
                    memberId = 999L,
                    courseId = 1L,
                ),
            )
        }
            .isInstanceOf(MemberNotFoundException::class.java)
    }

    @Test
    fun `강의 소유자는 모집을 마감할 수 있다`() {
        // Arrange
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))

        val created = service.createCourse(
            createCourseCommand(),
            1L,
        )

        service.openCourse(
            CourseStatusCommand(
                memberId = 1L,
                courseId = created.courseId,
            ),
        )

        // Act
        val result = service.closeCourse(
            CourseStatusCommand(
                memberId = 1L,
                courseId = created.courseId,
            ),
        )

        // Assert
        assertThat(result.status).isEqualTo(CourseStatus.CLOSED)
    }

    @Test
    fun `강의 소유자가 아닌 회원은 모집을 마감할 수 없다`() {
        // Arrange
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CREATOR))

        val created = service.createCourse(
            createCourseCommand(),
            1L,
        )

        // ACT & Assert
        assertThatThrownBy {
            service.closeCourse(
                CourseStatusCommand(
                    memberId = 2L,
                    courseId = created.courseId,
                ),
            )
        }
            .isInstanceOf(CourseAccessDeniedException::class.java)
    }

    private fun createCourseCommand(
        capacity: Int = 2,
    ) = CreateCourseCommand(
        title = "Kotlin Basic",
        description = "Kotlin course",
        price = Money(1L),
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

    private class RecordingCourseQueryHandler : CourseQueryHandler {
        val requestedCourseIds = mutableListOf<Long>()
        val requestedStatuses = mutableListOf<CourseStatus?>()

        override fun getCourse(courseId: Long): CourseResult {
            requestedCourseIds += courseId
            return courseResult(courseId = courseId)
        }

        override fun getCourses(status: CourseStatus?): List<CourseResult> {
            requestedStatuses += status
            return listOf(courseResult(status = status ?: CourseStatus.DRAFT))
        }
    }

    private class RecordingCourseCommandHandler : CourseCommandHandler {
        val createdCommands = mutableListOf<Pair<CreateCourseCommand, Long>>()
        val openedCommands = mutableListOf<CourseStatusCommand>()
        val closedCommands = mutableListOf<CourseStatusCommand>()

        override fun createCourse(
            command: CreateCourseCommand,
            creatorId: Long,
        ): CourseResult {
            createdCommands += command to creatorId
            return courseResult(creatorId = creatorId)
        }

        override fun openCourse(command: CourseStatusCommand): CourseResult {
            openedCommands += command
            return courseResult(courseId = command.courseId, creatorId = command.memberId, status = CourseStatus.OPEN)
        }

        override fun closeCourse(command: CourseStatusCommand): CourseResult {
            closedCommands += command
            return courseResult(courseId = command.courseId, creatorId = command.memberId, status = CourseStatus.CLOSED)
        }
    }

    private companion object {
        fun courseResult(
            courseId: Long = 1L,
            creatorId: Long = 1L,
            status: CourseStatus = CourseStatus.DRAFT,
        ): CourseResult = CourseResult(
            courseId = courseId,
            creatorId = creatorId,
            title = "Kotlin Basic",
            description = "Kotlin course",
            price = Money(1L),
            capacity = 2,
            seatLeftCount = 2,
            periodStart = LocalDateTime.of(2026, 6, 1, 0, 0),
            periodEnd = LocalDateTime.of(2026, 6, 30, 0, 0),
            status = status,
        )
    }
}
