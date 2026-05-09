package org.yechan.member

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.yechan.BaseEntity

@Entity
@Table(name = "members")
class MemberEntity private constructor(
    @field:Column(nullable = false, unique = true, length = 255)
    override var email: String,

    @field:Column(name = "password_hash", nullable = false)
    override var passwordHash: String,

    @field:Column(nullable = false, length = 30)
    override var name: String,

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 20)
    override var role: MemberRole,

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 20)
    override var status: MemberStatus,
) : BaseEntity(),
    MemberModel {
    override val memberId: Long?
        get() = id

    companion object {
        fun from(member: MemberModel): MemberEntity = MemberEntity(
            email = member.email,
            passwordHash = member.passwordHash,
            name = member.name,
            role = member.role,
            status = member.status,
        )
    }

    fun toDomain(): MemberModel = MemberModelData(
        memberId = id,
        email = email,
        passwordHash = passwordHash,
        name = name,
        role = role,
        status = status,
    )
}
