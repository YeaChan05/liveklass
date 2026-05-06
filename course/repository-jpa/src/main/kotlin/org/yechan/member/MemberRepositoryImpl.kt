package org.yechan.member

import org.springframework.stereotype.Repository

@Repository
class MemberRepositoryImpl(
    private val memberJpaRepository: MemberJpaRepository,
) : MemberRepository {
    override fun save(member: MemberModel): MemberModel = memberJpaRepository.save(MemberEntity.from(member)).toDomain()

    override fun existsByEmail(email: String): Boolean = memberJpaRepository.existsByEmail(email)

    override fun findByEmail(email: String): MemberModel? = memberJpaRepository.findByEmail(email)?.toDomain()

    override fun findById(id: Long): MemberModel? = memberJpaRepository.findById(id).orElse(null)?.toDomain()
}
