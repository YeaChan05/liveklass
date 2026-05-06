package org.yechan.member

import org.springframework.data.jpa.repository.JpaRepository

interface MemberJpaRepository : JpaRepository<MemberEntity, Long> {
    fun existsByEmail(email: String): Boolean

    fun findByEmail(email: String): MemberEntity?
}
