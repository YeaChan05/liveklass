package org.yechan.enrollment

class EnrollmentRepositoryImpl(
    private val enrollmentJpaRepository: EnrollmentJpaRepository,
) : EnrollmentRepository {
    override fun save(
        enrollment: EnrollmentModel,
        courseId: Long,
    ): EnrollmentModel = enrollmentJpaRepository.save(EnrollmentEntity.from(enrollment, courseId)).toDomain()

    override fun findById(enrollmentId: Long): EnrollmentModel? = enrollmentJpaRepository.findById(enrollmentId)
        .orElse(null)
        ?.toDomain()

    override fun findByMemberId(memberId: Long): List<EnrollmentModel> = enrollmentJpaRepository
        .findAllByMemberId(memberId)
        .map(EnrollmentEntity::toDomain)
}
