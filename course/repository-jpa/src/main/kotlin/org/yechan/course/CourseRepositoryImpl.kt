package org.yechan.course

class CourseRepositoryImpl(
    private val courseJpaRepository: CourseJpaRepository,
) : CourseRepository {
    override fun save(course: CourseModel): CourseModel = courseJpaRepository.save(CourseEntity.of(course, creatorId = course.creatorId!!))
        .toDomain()

    override fun findById(courseId: Long): CourseModel? = courseJpaRepository.findById(courseId).orElse(null)?.toDomain()

    override fun findByIdForUpdate(courseId: Long): CourseModel? = courseJpaRepository.findByIdForUpdate(courseId)?.toDomain()

    override fun findAll(): List<CourseModel> = courseJpaRepository.findAll().map(CourseEntity::toDomain)
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
