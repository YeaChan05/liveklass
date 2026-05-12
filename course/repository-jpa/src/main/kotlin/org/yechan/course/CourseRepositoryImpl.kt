package org.yechan.course

class CourseRepositoryImpl(
    private val courseJpaRepository: CourseJpaRepository,
) : CourseRepository {
    override fun save(course: CourseModel): CourseModel = courseJpaRepository.save(CourseEntity.of(course, creatorId = course.creatorId!!))
        .toDomain()

    override fun findById(courseId: Long): CourseModel? = courseJpaRepository.findById(courseId)
        .map(CourseEntity::toDomain)
        .orElse(null)

    override fun reserveSeatIfAvailable(courseId: Long): Boolean = courseJpaRepository.reserveSeatIfAvailable(
        courseId = courseId,
        status = CourseStatus.OPEN,
    ) == 1

    override fun findAll(): List<CourseModel> = courseJpaRepository.findAll()
        .map(CourseEntity::toDomain)

    override fun findAllByStatus(status: CourseStatus): List<CourseModel> = courseJpaRepository.findAllByStatus(status)
        .map(CourseEntity::toDomain)

    override fun findAllOpendCoursesByIds(courseIds: Collection<Long>): List<CourseModel> = courseJpaRepository.findAllOpened(courseIds)
        .map(CourseEntity::toDomain)

    override fun releaseSeatIfPossible(courseId: Long): Boolean = courseJpaRepository.releaseSeatIfPossible(courseId) == 1
}

private fun CourseEntity.toDomain(): CourseModel = CourseModelData(
    courseId = id,
    creatorId = creatorId,
    title = title,
    description = description,
    price = Money(priceAmount),
    capacity = capacity,
    seatLeftCount = seatLeftCount,
    periodStart = periodStart,
    periodEnd = periodEnd,
    status = status,
)
