package org.yechan.member

interface MemberIdentifier {
    val memberId: Long?
}

interface MemberProps {
    val email: String
    val passwordHash: String
    val name: String
    val role: MemberRole
    val status: MemberStatus
}

interface MemberModel :
    MemberProps,
    MemberIdentifier {
    fun validateMemberStatus()
}

data class MemberModelData(
    override val memberId: Long? = null,
    override val email: String,
    override val passwordHash: String,
    override val name: String,
    override val role: MemberRole,
    override val status: MemberStatus = MemberStatus.ACTIVE,
) : MemberModel {
    override fun validateMemberStatus() {
        if (status != MemberStatus.ACTIVE) {
            throw InactiveMemberException()
        }
    }
}

enum class MemberRole {
    CREATOR,
    CLASSMATE,
    ADMIN,
}

enum class MemberStatus {
    ACTIVE,
    DELETED,
}
