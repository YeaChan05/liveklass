package org.yechan.course

import org.springframework.transaction.annotation.Transactional
import org.yechan.member.MemberModel
import org.yechan.member.MemberNotFoundException
import org.yechan.member.MemberRepository

interface CourseUseCase {
    fun getCourse(courseId: Long): CourseResult

    fun getCourses(): List<CourseResult>

    fun createCourse(command: CreateCourseCommand): CourseResult

    fun openCourse(command: CourseStatusCommand): CourseResult

    fun closeCourse(command: CourseStatusCommand): CourseResult
}

@Transactional(readOnly = true)
class CourseService(
    private val memberRepository: MemberRepository,
    private val courseRepository: CourseRepository,
) : CourseUseCase {
    override fun getCourse(courseId: Long): CourseResult = CourseResult.from(
        courseRepository.findById(courseId) ?: throw CourseNotFoundException(),
    )

    override fun getCourses(): List<CourseResult> = courseRepository.findAll().map(CourseResult::from)

    @Transactional
    override fun createCourse(command: CreateCourseCommand): CourseResult {
        val creator = activeMember(command.creatorId)
        val course = CourseModel(
            creatorId = requireNotNull(creator.memberId),
            title = command.title,
            description = command.description,
            price = command.price,
            capacity = command.capacity,
            periodStart = command.periodStart,
            periodEnd = command.periodEnd,
        )

        return CourseResult.from(courseRepository.save(course))
    }

    @Transactional
    override fun openCourse(command: CourseStatusCommand): CourseResult {
        val course = ownedCourse(command.courseId, command.memberId)
        return CourseResult.from(courseRepository.save(course.open()))
    }

    @Transactional
    override fun closeCourse(command: CourseStatusCommand): CourseResult {
        val course = ownedCourse(command.courseId, command.memberId)
        return CourseResult.from(courseRepository.save(course.close()))
    }

    private fun activeMember(memberId: Long): MemberModel {
        val member = memberRepository.findById(memberId) ?: throw MemberNotFoundException()
        member.validateMemberStatus()
        return member
    }

    private fun ownedCourse(
        courseId: Long,
        memberId: Long,
    ): CourseModel {
        activeMember(memberId)
        val course = courseRepository.findById(courseId) ?: throw CourseNotFoundException()
        if (course.creatorId != memberId) throw CourseAccessDeniedException()
        return course
    }
}
