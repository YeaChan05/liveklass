package org.yechan.member

interface MemberRepository {
    fun save(member: MemberModel): MemberModel

    fun existsByEmail(email: String): Boolean

    fun findByEmail(email: String): MemberModel?

    fun findById(id: Long): MemberModel?
}
