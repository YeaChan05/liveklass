package org.yechan.course

import org.springframework.transaction.annotation.Transactional
import org.yechan.member.MemberModel
import org.yechan.member.MemberNotFoundException
import org.yechan.member.MemberRepository

interface CourseUseCase {
    fun getCourse(courseId: Long): CourseResult

    fun getCourses(status: CourseStatus? = null): List<CourseResult>

    fun createCourse(command: CreateCourseCommand, creatorId: Long): CourseResult

    fun openCourse(command: CourseStatusCommand): CourseResult

    fun closeCourse(command: CourseStatusCommand): CourseResult
}

interface CourseReader {
    fun getCourse(courseId: Long): CourseResult

    fun getCourses(status: CourseStatus? = null): List<CourseResult>

    fun getOpenedCoursesByIds(courseIds: Collection<Long>): List<CourseResult>
}

interface CourseWriter {
    fun createCourse(command: CreateCourseCommand, creatorId: Long): CourseResult

    fun openCourse(command: CourseStatusCommand): CourseResult

    fun closeCourse(command: CourseStatusCommand): CourseResult
}

class CourseService(
    private val reader: CourseReader,
    private val writer: CourseWriter,
) : CourseUseCase {
    override fun getCourse(courseId: Long): CourseResult = reader.getCourse(courseId)

    override fun getCourses(status: CourseStatus?): List<CourseResult> = reader.getCourses(status)

    override fun createCourse(
        command: CreateCourseCommand,
        creatorId: Long,
    ): CourseResult = writer.createCourse(command, creatorId)

    override fun openCourse(command: CourseStatusCommand): CourseResult = writer.openCourse(command)

    override fun closeCourse(command: CourseStatusCommand): CourseResult = writer.closeCourse(command)
}

@Transactional(readOnly = true)
class CourseRepositoryReader(
    private val courseRepository: CourseRepository,
) : CourseReader {
    override fun getCourse(courseId: Long): CourseResult = (courseRepository.findById(courseId) ?: throw CourseNotFoundException()).toResult()

    override fun getCourses(status: CourseStatus?): List<CourseResult> = when (status) {
        null -> courseRepository.findAll()
        else -> courseRepository.findAllByStatus(status)
    }.map(CourseModel::toResult)

    override fun getOpenedCoursesByIds(courseIds: Collection<Long>): List<CourseResult> = courseRepository.findAllOpenedCoursesByIds(courseIds).map(CourseModel::toResult)
}

@Transactional(readOnly = true)
class CourseRepositoryWriter(
    private val memberRepository: MemberRepository,
    private val courseRepository: CourseRepository,
) : CourseWriter {
    @Transactional
    override fun createCourse(
        command: CreateCourseCommand,
        creatorId: Long,
    ): CourseResult {
        val creator = activeMember(creatorId)
        val course = CourseModelData(
            creatorId = creator.memberId,
            title = command.title,
            description = command.description,
            price = command.price,
            capacity = command.capacity,
            periodStart = command.periodStart,
            periodEnd = command.periodEnd,
        )
        return courseRepository.save(course).toResult()
    }

    @Transactional
    override fun openCourse(command: CourseStatusCommand): CourseResult {
        val course = ownedCourse(command.courseId, command.memberId)
        return courseRepository.save(course.open()).toResult()
    }

    @Transactional
    override fun closeCourse(command: CourseStatusCommand): CourseResult {
        val course = ownedCourse(command.courseId, command.memberId)
        return courseRepository.save(course.close()).toResult()
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
        val member = activeMember(memberId)
        val course = courseRepository.findById(courseId) ?: throw CourseNotFoundException()
        if (course.creatorId != member.memberId) {
            throw CourseAccessDeniedException()
        }
        return course
    }
}

private fun CourseModel.toResult(): CourseResult = CourseResult(
    courseId = requireNotNull(courseId),
    creatorId = requireNotNull(creatorId),
    title = title,
    description = description,
    price = price,
    capacity = capacity,
    seatLeftCount = seatLeftCount,
    periodStart = periodStart,
    periodEnd = periodEnd,
    status = status,
)
