package org.yechan.member

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class MemberModelTest {

    @Test
    fun `활성 상태인 회원은 상태 검증을 통과한다`() {
        val member = MemberModel(
            memberId = 1L,
            email = "test@example.com",
            passwordHash = "hash",
            name = "name",
            role = MemberRole.CLASSMATE,
            status = MemberStatus.ACTIVE,
        )

        assertThatCode { member.validateMemberStatus() }
            .doesNotThrowAnyException()
    }

    @Test
    fun `삭제된 상태인 회원은 상태 검증 시 예외가 발생한다`() {
        val member = MemberModel(
            memberId = 1L,
            email = "test@example.com",
            passwordHash = "hash",
            name = "name",
            role = MemberRole.CLASSMATE,
            status = MemberStatus.DELETED,
        )

        assertThatThrownBy { member.validateMemberStatus() }
            .isInstanceOf(InactiveMemberException::class.java)
    }
}
