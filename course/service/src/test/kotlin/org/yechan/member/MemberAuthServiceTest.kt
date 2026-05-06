package org.yechan.member

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.yechan.AuthTokenProperties
import org.yechan.AuthTokenValue
import org.yechan.BusinessException
import org.yechan.PasswordHashEncoder
import org.yechan.TokenGenerator
import org.yechan.TokenVerifier
import java.time.LocalDateTime
import java.util.Collections

class MemberAuthServiceTest {
    private val members = FakeMemberRepository()
    private val refreshTokens = FakeRefreshTokenRepository()
    private val passwordHashEncoder = FakePasswordHashEncoder()
    private val tokenGenerator = FakeTokenGenerator()
    private val tokenVerifier = FakeTokenVerifier()
    private val authTokenProperties = AuthTokenProperties("test-salt", 1800, 604800)
    private val service =
        MemberAuthService(members, refreshTokens, passwordHashEncoder, tokenGenerator, tokenVerifier, authTokenProperties)

    @Test
    fun `signup stores active member with hashed password`() {
        val result = service.signup(SignupCommand("student@example.com", "password1234!", " 홍길동 ", MemberRole.STUDENT))

        val saved = members.findByEmail("student@example.com")
        assertThat(result.email).isEqualTo("student@example.com")
        assertThat(result.name).isEqualTo("홍길동")
        assertThat(result.role).isEqualTo(MemberRole.STUDENT)
        assertThat(saved?.passwordHash).isEqualTo("hashed:password1234!")
        assertThat(saved?.passwordHash).isNotEqualTo("password1234!")
        assertThat(saved?.status).isEqualTo(MemberStatus.ACTIVE)
    }

    @Test
    fun `signup rejects duplicate email`() {
        service.signup(SignupCommand("student@example.com", "password1234!", "홍길동", MemberRole.STUDENT))

        assertThatThrownBy {
            service.signup(SignupCommand("student@example.com", "password1234!", "홍길동", MemberRole.STUDENT))
        }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("이미 사용 중인 이메일입니다.")
    }

    @Test
    fun `login returns tokens and replaces previous refresh token`() {
        val member = service.signup(SignupCommand("student@example.com", "password1234!", "홍길동", MemberRole.STUDENT))

        val result = service.login(LoginCommand("student@example.com", "password1234!"))

        assertThat(result.accessToken).isEqualTo("access-${member.userId}")
        assertThat(result.refreshToken).isEqualTo("refresh-${member.userId}")
        assertThat(result.tokenType).isEqualTo("Bearer")
        assertThat(result.expiresIn).isEqualTo(1800)
        assertThat(result.user.email).isEqualTo("student@example.com")
        assertThat(refreshTokens.savedByUserId(member.userId)).hasSize(1)
        assertThat(refreshTokens.savedByUserId(member.userId).single().tokenHash).isNotEqualTo("refresh-${member.userId}")
        assertThat(refreshTokens.savedByUserId(member.userId).single().tokenHash).isNotBlank()
    }

    @Test
    fun `login hides whether email or password is wrong`() {
        service.signup(SignupCommand("student@example.com", "password1234!", "홍길동", MemberRole.STUDENT))

        assertThatThrownBy { service.login(LoginCommand("student@example.com", "wrong-password")) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.")

        assertThatThrownBy { service.login(LoginCommand("missing@example.com", "password1234!")) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.")
    }

    @Test
    fun `login rejects deleted member`() {
        val member = service.signup(SignupCommand("student@example.com", "password1234!", "홍길동", MemberRole.STUDENT))
        members.updateStatus(member.userId, MemberStatus.DELETED)

        assertThatThrownBy { service.login(LoginCommand("student@example.com", "password1234!")) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.")
    }

    @Test
    fun `refresh access token requires stored matching refresh token`() {
        val member = service.signup(SignupCommand("student@example.com", "password1234!", "홍길동", MemberRole.STUDENT))
        val login = service.login(LoginCommand("student@example.com", "password1234!"))

        val refreshed = service.refresh(RefreshTokenCommand(login.refreshToken))

        assertThat(refreshed.accessToken).isEqualTo("access-${member.userId}")
        assertThat(refreshed.tokenType).isEqualTo("Bearer")
        assertThat(refreshed.expiresIn).isEqualTo(1800)

        assertThatThrownBy { service.refresh(RefreshTokenCommand("tampered")) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("유효하지 않은 Refresh Token입니다.")
    }

    @Test
    fun `refresh rejects token when stored token belongs to another user`() {
        val member = service.signup(SignupCommand("student@example.com", "password1234!", "홍길동", MemberRole.STUDENT))
        val login = service.login(LoginCommand("student@example.com", "password1234!"))
        refreshTokens.replaceSavedToken(member.userId, userId = 999L)

        assertThatThrownBy { service.refresh(RefreshTokenCommand(login.refreshToken)) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("유효하지 않은 Refresh Token입니다.")
    }

    @Test
    fun `refresh rejects expired stored token`() {
        val member = service.signup(SignupCommand("student@example.com", "password1234!", "홍길동", MemberRole.STUDENT))
        val login = service.login(LoginCommand("student@example.com", "password1234!"))
        refreshTokens.replaceSavedToken(member.userId, expiresAt = LocalDateTime.now().minusSeconds(1))

        assertThatThrownBy { service.refresh(RefreshTokenCommand(login.refreshToken)) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("유효하지 않은 Refresh Token입니다.")
    }

    @Test
    fun `refresh rejects token when member is missing or deleted`() {
        val member = service.signup(SignupCommand("student@example.com", "password1234!", "홍길동", MemberRole.STUDENT))
        val login = service.login(LoginCommand("student@example.com", "password1234!"))

        members.delete(member.userId)
        assertThatThrownBy { service.refresh(RefreshTokenCommand(login.refreshToken)) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("유효하지 않은 Refresh Token입니다.")

        val deletedMember = service.signup(SignupCommand("deleted@example.com", "password1234!", "이삭제", MemberRole.STUDENT))
        val deletedLogin = service.login(LoginCommand("deleted@example.com", "password1234!"))
        members.updateStatus(deletedMember.userId, MemberStatus.DELETED)

        assertThatThrownBy { service.refresh(RefreshTokenCommand(deletedLogin.refreshToken)) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("유효하지 않은 Refresh Token입니다.")
    }

    @Test
    fun `refresh rejects token with unparsable authentication name`() {
        tokenVerifier.authenticationName = "not-a-number"

        assertThatThrownBy { service.refresh(RefreshTokenCommand("refresh-1")) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("유효하지 않은 Refresh Token입니다.")
    }

    @Test
    fun `get current user returns active member`() {
        val member = service.signup(SignupCommand("student@example.com", "password1234!", "홍길동", MemberRole.STUDENT))

        val result = service.getCurrentUser(member.userId)

        assertThat(result.id).isEqualTo(member.userId)
        assertThat(result.email).isEqualTo("student@example.com")
        assertThat(result.name).isEqualTo("홍길동")
        assertThat(result.role).isEqualTo(MemberRole.STUDENT)
        assertThat(result.status).isEqualTo(MemberStatus.ACTIVE)
    }

    @Test
    fun `get current user rejects unknown user`() {
        assertThatThrownBy { service.getCurrentUser(404L) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("인증이 필요합니다.")
    }

    private class FakeMemberRepository : MemberRepository {
        private val members = linkedMapOf<Long, MemberModel>()
        private var nextId = 1L

        override fun save(member: MemberModel): MemberModel {
            val saved = member.copy(memberId = member.memberId ?: nextId++)
            members[saved.memberId!!] = saved
            return saved
        }

        override fun existsByEmail(email: String): Boolean = members.values.any { it.email == email }

        override fun findByEmail(email: String): MemberModel? = members.values.firstOrNull { it.email == email }

        override fun findById(id: Long): MemberModel? = members[id]

        fun updateStatus(
            id: Long,
            status: MemberStatus,
        ) {
            members[id] = members.getValue(id).copy(status = status)
        }

        fun delete(id: Long) {
            members.remove(id)
        }
    }

    private class FakeRefreshTokenRepository : RefreshTokenRepository {
        private val tokens = mutableListOf<RefreshTokenModel>()

        override fun replace(refreshToken: RefreshTokenModel) {
            deleteByUserId(refreshToken.userId)
            tokens += refreshToken
        }

        override fun findByUserId(userId: Long): RefreshTokenModel? = tokens.firstOrNull { it.userId == userId }

        override fun findByTokenHash(tokenHash: String): RefreshTokenModel? = tokens.firstOrNull { it.tokenHash == tokenHash }

        override fun deleteByUserId(userId: Long) {
            tokens.removeIf { it.userId == userId }
        }

        fun savedByUserId(userId: Long): List<RefreshTokenModel> = tokens.filter { it.userId == userId }

        fun replaceSavedToken(
            sourceUserId: Long,
            userId: Long = sourceUserId,
            expiresAt: LocalDateTime = tokens.first { it.userId == sourceUserId }.expiresAt,
        ) {
            val token = tokens.first { it.userId == sourceUserId }
            tokens.remove(token)
            tokens += token.copy(userId = userId, expiresAt = expiresAt)
        }
    }

    private class FakePasswordHashEncoder : PasswordHashEncoder {
        override fun encode(password: String): String = "hashed:$password"

        override fun matches(password: String, encodedPassword: String): Boolean = encodedPassword == encode(password)
    }

    private class FakeTokenGenerator : TokenGenerator {
        override fun generate(memberId: Long?): AuthTokenValue = AuthTokenValue(
            accessToken = "access-$memberId",
            refreshToken = "refresh-$memberId",
            expiresIn = 1800,
        )
    }

    private class FakeTokenVerifier : TokenVerifier {
        var authenticationName: String? = null

        override fun verify(token: String): Authentication {
            val userId = token.removePrefix("refresh-").takeIf { it != token }?.toLongOrNull()
                ?: throw BadCredentialsException("Invalid token")
            return UsernamePasswordAuthenticationToken(authenticationName ?: userId.toString(), token, Collections.emptyList())
        }
    }
}
