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
import org.yechan.TokenExpirationResolver
import org.yechan.TokenGenerator
import org.yechan.TokenVerifier
import java.time.Duration
import java.time.LocalDateTime
import java.util.Collections

class MemberAuthServiceTest {
    private val members = FakeMemberRepository()
    private val refreshTokens = FakeRefreshTokenRepository()
    private val passwordHashEncoder = FakePasswordHashEncoder()
    private val tokenGenerator = FakeTokenGenerator()
    private val tokenVerifier = FakeTokenVerifier()
    private val tokenExpirationResolver = FakeTokenExpirationResolver()
    private val accessTokenBlacklistRepository = FakeAccessTokenBlacklistRepository()
    private val authTokenProperties = AuthTokenProperties("test-salt", 1800, 604800)
    private val service =
        MemberAuthService(
            members,
            refreshTokens,
            passwordHashEncoder,
            tokenGenerator,
            tokenVerifier,
            tokenExpirationResolver,
            accessTokenBlacklistRepository,
            authTokenProperties,
        )

    @Test
    fun `회원가입은 해시된 비밀번호로 활성 회원을 저장한다`() {
        val result = service.signup(
            SignupCommand(
                "student@example.com",
                "password1234!",
                " 홍길동 ",
                MemberRole.CLASSMATE,
            ),
        )

        val saved = members.findByEmail("student@example.com")
        assertThat(result.email).isEqualTo("student@example.com")
        assertThat(result.name).isEqualTo("홍길동")
        assertThat(result.role).isEqualTo(MemberRole.CLASSMATE)
        assertThat(saved?.passwordHash).isEqualTo("hashed:password1234!")
        assertThat(saved?.passwordHash).isNotEqualTo("password1234!")
        assertThat(saved?.status).isEqualTo(MemberStatus.ACTIVE)
    }

    @Test
    fun `회원가입은 중복 이메일을 거부한다`() {
        service.signup(
            SignupCommand(
                "student@example.com",
                "password1234!",
                "홍길동",
                MemberRole.CLASSMATE,
            ),
        )

        assertThatThrownBy {
            service.signup(
                SignupCommand(
                    "student@example.com",
                    "password1234!",
                    "홍길동",
                    MemberRole.CLASSMATE,
                ),
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("이미 사용 중인 이메일입니다.")
    }

    @Test
    fun `로그인은 토큰을 반환하고 이전 리프레시 토큰을 교체한다`() {
        val member = service.signup(
            SignupCommand(
                "student@example.com",
                "password1234!",
                "홍길동",
                MemberRole.CLASSMATE,
            ),
        )

        val result = service.login(LoginCommand("student@example.com", "password1234!"))

        assertThat(result.accessToken).isEqualTo("access-${member.userId}")
        assertThat(result.refreshToken).isEqualTo("refresh-${member.userId}")
        assertThat(result.tokenType).isEqualTo("Bearer")
        assertThat(result.expiresIn).isEqualTo(1800)
        assertThat(result.user.email).isEqualTo("student@example.com")
        assertThat(refreshTokens.savedByUserId(member.userId)).hasSize(1)
        assertThat(
            refreshTokens.savedByUserId(member.userId).single().tokenHash,
        ).isNotEqualTo("refresh-${member.userId}")
        assertThat(refreshTokens.savedByUserId(member.userId).single().tokenHash).isNotBlank()
    }

    @Test
    fun `로그인은 이메일과 비밀번호 중 무엇이 틀렸는지 숨긴다`() {
        service.signup(
            SignupCommand(
                "student@example.com",
                "password1234!",
                "홍길동",
                MemberRole.CLASSMATE,
            ),
        )

        assertThatThrownBy { service.login(LoginCommand("student@example.com", "wrong-password")) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.")

        assertThatThrownBy { service.login(LoginCommand("missing@example.com", "password1234!")) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.")
    }

    @Test
    fun `로그아웃은 액세스 토큰을 블랙리스트에 등록하고 리프레시 토큰을 삭제한다`() {
        val member = service.signup(
            SignupCommand(
                "student@example.com",
                "password1234!",
                "홍길동",
                MemberRole.CLASSMATE,
            ),
        )
        val loginResult = service.login(LoginCommand("student@example.com", "password1234!"))

        service.logout(LogoutCommand(member.userId, loginResult.accessToken))

        assertThat(accessTokenBlacklistRepository.contains(loginResult.accessToken)).isTrue()
        assertThat(accessTokenBlacklistRepository.ttlOf(loginResult.accessToken)).isEqualTo(
            Duration.ofMinutes(
                6,
            ),
        )
        assertThat(refreshTokens.findByUserId(member.userId)).isNull()
    }

    @Test
    fun `로그인은 삭제된 회원을 거부한다`() {
        val member = service.signup(
            SignupCommand(
                "student@example.com",
                "password1234!",
                "홍길동",
                MemberRole.CLASSMATE,
            ),
        )
        members.updateStatus(member.userId, MemberStatus.DELETED)

        assertThatThrownBy { service.login(LoginCommand("student@example.com", "password1234!")) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.")
    }

    @Test
    fun `액세스 토큰 재발급은 저장된 일치 리프레시 토큰을 요구한다`() {
        val member = service.signup(
            SignupCommand(
                "student@example.com",
                "password1234!",
                "홍길동",
                MemberRole.CLASSMATE,
            ),
        )
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
    fun `재발급은 저장된 토큰의 사용자가 다르면 거부한다`() {
        val member = service.signup(
            SignupCommand(
                "student@example.com",
                "password1234!",
                "홍길동",
                MemberRole.CLASSMATE,
            ),
        )
        val login = service.login(LoginCommand("student@example.com", "password1234!"))
        refreshTokens.replaceSavedToken(member.userId, userId = 999L)

        assertThatThrownBy { service.refresh(RefreshTokenCommand(login.refreshToken)) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("유효하지 않은 Refresh Token입니다.")
    }

    @Test
    fun `재발급은 만료된 저장 토큰을 거부한다`() {
        val member = service.signup(
            SignupCommand(
                "student@example.com",
                "password1234!",
                "홍길동",
                MemberRole.CLASSMATE,
            ),
        )
        val login = service.login(LoginCommand("student@example.com", "password1234!"))
        refreshTokens.replaceSavedToken(
            member.userId,
            expiresAt = LocalDateTime.now().minusSeconds(1),
        )

        assertThatThrownBy { service.refresh(RefreshTokenCommand(login.refreshToken)) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("유효하지 않은 Refresh Token입니다.")
    }

    @Test
    fun `재발급은 회원이 없거나 삭제된 경우 거부한다`() {
        val member = service.signup(
            SignupCommand(
                "student@example.com",
                "password1234!",
                "홍길동",
                MemberRole.CLASSMATE,
            ),
        )
        val login = service.login(LoginCommand("student@example.com", "password1234!"))

        members.delete(member.userId)
        assertThatThrownBy { service.refresh(RefreshTokenCommand(login.refreshToken)) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("유효하지 않은 Refresh Token입니다.")

        val deletedMember = service.signup(
            SignupCommand(
                "deleted@example.com",
                "password1234!",
                "이삭제",
                MemberRole.CLASSMATE,
            ),
        )
        val deletedLogin = service.login(LoginCommand("deleted@example.com", "password1234!"))
        members.updateStatus(deletedMember.userId, MemberStatus.DELETED)

        assertThatThrownBy { service.refresh(RefreshTokenCommand(deletedLogin.refreshToken)) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("비활성화된 회원입니다.")
    }

    @Test
    fun `재발급은 인증 이름을 숫자로 해석할 수 없으면 거부한다`() {
        tokenVerifier.authenticationName = "not-a-number"

        assertThatThrownBy { service.refresh(RefreshTokenCommand("refresh-1")) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("유효하지 않은 Refresh Token입니다.")
    }

    @Test
    fun `현재 사용자 조회는 활성 회원을 반환한다`() {
        val member = service.signup(
            SignupCommand(
                "student@example.com",
                "password1234!",
                "홍길동",
                MemberRole.CLASSMATE,
            ),
        )

        val result = service.getCurrentUser(member.userId)

        assertThat(result.id).isEqualTo(member.userId)
        assertThat(result.email).isEqualTo("student@example.com")
        assertThat(result.name).isEqualTo("홍길동")
        assertThat(result.role).isEqualTo(MemberRole.CLASSMATE)
        assertThat(result.status).isEqualTo(MemberStatus.ACTIVE)
    }

    @Test
    fun `현재 사용자 조회는 알 수 없는 사용자를 거부한다`() {
        assertThatThrownBy { service.getCurrentUser(404L) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("회원 정보를 찾을 수 없습니다.")
    }

    private class FakeMemberRepository : MemberRepository {
        private val members = linkedMapOf<Long, MemberModel>()
        private var nextId = 1L

        override fun save(member: MemberModel): MemberModel {
            val saved = MemberModelData(
                memberId = member.memberId ?: nextId++,
                email = member.email,
                passwordHash = member.passwordHash,
                name = member.name,
                role = member.role,
                status = member.status,
            )
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
            val current = members.getValue(id)
            members[id] = MemberModelData(
                memberId = current.memberId,
                email = current.email,
                passwordHash = current.passwordHash,
                name = current.name,
                role = current.role,
                status = status,
            )
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
            tokens += RefreshTokenModelData(
                refreshTokenId = token.refreshTokenId,
                userId = userId,
                tokenHash = token.tokenHash,
                expiresAt = expiresAt,
            )
        }
    }

    private class FakePasswordHashEncoder : PasswordHashEncoder {
        override fun encode(password: String): String = "hashed:$password"

        override fun matches(password: String, encodedPassword: String): Boolean = encodedPassword == encode(password)
    }

    private class FakeTokenGenerator : TokenGenerator {
        override fun generate(
            memberId: Long?,
            roles: Set<String>,
        ): AuthTokenValue = AuthTokenValue(
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
            return UsernamePasswordAuthenticationToken(
                authenticationName ?: userId.toString(),
                token,
                Collections.emptyList(),
            )
        }
    }

    private class FakeTokenExpirationResolver : TokenExpirationResolver {
        override fun remainingTime(token: String): Duration = Duration.ofMinutes(5)
    }

    private class FakeAccessTokenBlacklistRepository : AccessTokenBlacklistRepository {
        private val tokens = mutableMapOf<String, Duration>()

        override fun blacklist(
            token: String,
            ttl: Duration,
        ) {
            tokens[token] = ttl
        }

        override fun contains(token: String): Boolean = tokens.containsKey(token)

        fun ttlOf(token: String): Duration? = tokens[token]
    }
}
