package org.yechan.member

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.yechan.BaseEntity
import org.yechan.course.CourseEntity
import org.yechan.enrollment.EnrollmentEntity

@Entity
@Table(name = "members")
class MemberEntity private constructor(
    @field:Column(nullable = false, unique = true, length = 255)
    var email: String,
    @field:Column(name = "password_hash", nullable = false)
    var passwordHash: String,
    @field:Column(nullable = false, length = 30)
    var name: String,
    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 20)
    var role: MemberRole,
    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 20)
    var status: MemberStatus,
) : BaseEntity() {
    @field:OneToMany(mappedBy = "creator")
    val courses: MutableList<CourseEntity> = mutableListOf()

    @field:OneToMany(mappedBy = "member")
    val enrollments: MutableList<EnrollmentEntity> = mutableListOf()

    companion object {
        fun from(member: MemberModel): MemberEntity = MemberEntity(
            email = member.email,
            passwordHash = member.passwordHash,
            name = member.name,
            role = member.role,
            status = member.status,
        )
    }

    fun toDomain(): MemberModel = MemberModel(
        memberId = id,
        email = email,
        passwordHash = passwordHash,
        name = name,
        role = role,
        status = status,
    )
}
