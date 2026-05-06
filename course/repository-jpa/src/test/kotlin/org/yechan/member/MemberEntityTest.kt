package org.yechan.member

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MemberEntityTest {
    @Test
    fun `from creates entity from member model`() {
        val member = MemberModel(
            memberId = 1L,
            email = "student@example.com",
            passwordHash = "hashed-password",
            name = "홍길동",
            role = MemberRole.STUDENT,
            status = MemberStatus.ACTIVE,
        )

        val entity = MemberEntity.from(member)

        assertThat(entity.email).isEqualTo("student@example.com")
        assertThat(entity.passwordHash).isEqualTo("hashed-password")
        assertThat(entity.name).isEqualTo("홍길동")
        assertThat(entity.role).isEqualTo(MemberRole.STUDENT)
        assertThat(entity.status).isEqualTo(MemberStatus.ACTIVE)
    }

    @Test
    fun `toDomain creates member model from entity`() {
        val entity = MemberEntity.from(
            MemberModel(
                email = "creator@example.com",
                passwordHash = "hashed-password",
                name = "김크리에이터",
                role = MemberRole.CREATOR,
                status = MemberStatus.DELETED,
            ),
        )

        val member = entity.toDomain()

        assertThat(member.memberId).isNull()
        assertThat(member.email).isEqualTo("creator@example.com")
        assertThat(member.passwordHash).isEqualTo("hashed-password")
        assertThat(member.name).isEqualTo("김크리에이터")
        assertThat(member.role).isEqualTo(MemberRole.CREATOR)
        assertThat(member.status).isEqualTo(MemberStatus.DELETED)
    }
}
