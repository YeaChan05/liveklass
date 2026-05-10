package org.yechan.member

import org.springframework.security.authentication.BadCredentialsException
import org.springframework.transaction.annotation.Transactional
import org.yechan.AuthTokenProperties
import org.yechan.PasswordHashEncoder
import org.yechan.TokenExpirationResolver
import org.yechan.TokenGenerator
import org.yechan.TokenVerifier
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDateTime

interface MemberAuthUseCase {
    fun signup(command: SignupCommand): SignupResult

    fun login(command: LoginCommand): LoginResult

    fun refresh(command: RefreshTokenCommand): RefreshTokenResult

    fun logout(command: LogoutCommand)

    fun getCurrentUser(userId: Long): CurrentMemberResult

    fun getCurrentUserByEmail(email: String): CurrentMemberResult
}

@Transactional(readOnly = true)
class MemberAuthService(
    private val memberRepository: MemberRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordHashEncoder: PasswordHashEncoder,
    private val tokenGenerator: TokenGenerator,
    private val tokenVerifier: TokenVerifier,
    private val tokenExpirationResolver: TokenExpirationResolver,
    private val accessTokenBlacklistRepository: AccessTokenBlacklistRepository,
    private val authTokenProperties: AuthTokenProperties,
) : MemberAuthUseCase {
    @Transactional
    override fun signup(command: SignupCommand): SignupResult {
        val email = command.email.trim()
        if (memberRepository.existsByEmail(email)) {
            throw DuplicateMemberEmailException()
        }

        val member =
            memberRepository.save(
                MemberModelData(
                    email = email,
                    passwordHash = passwordHashEncoder.encode(command.password),
                    name = command.name.trim(),
                    role = command.role,
                    status = MemberStatus.ACTIVE,
                ),
            )
        return SignupResult(
            userId = requireNotNull(member.memberId),
            email = member.email,
            name = member.name,
            role = member.role,
        )
    }

    @Transactional
    override fun login(command: LoginCommand): LoginResult {
        val member = memberRepository.findByEmail(command.email.trim())
            ?: throw MemberAuthenticationException()
        if (member.status != MemberStatus.ACTIVE ||
            !passwordHashEncoder.matches(command.password, member.passwordHash)
        ) {
            throw MemberAuthenticationException()
        }

        val memberId = requireNotNull(member.memberId)
        val token = tokenGenerator.generate(memberId, roles = setOf(member.role.name))
        refreshTokenRepository.replace(
            RefreshTokenModelData(
                userId = memberId,
                tokenHash = token.refreshToken.toRefreshTokenHash(),
                expiresAt = LocalDateTime.now().plusSeconds(authTokenProperties.refreshExpiresIn),
            ),
        )

        return LoginResult(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            tokenType = TOKEN_TYPE,
            expiresIn = token.expiresIn,
            user = member.toSummary(),
        )
    }

    override fun refresh(command: RefreshTokenCommand): RefreshTokenResult {
        val authentication =
            try {
                tokenVerifier.verify(command.refreshToken)
            } catch (_: BadCredentialsException) {
                throw InvalidRefreshTokenException()
            }
        val userId = authentication.name.toLongOrNull()
            ?: throw InvalidRefreshTokenException()

        val refreshToken =
            refreshTokenRepository.findByTokenHash(command.refreshToken.toRefreshTokenHash())
                ?: throw InvalidRefreshTokenException()
        validateUserIdMatch(refreshToken, userId)
        verifyTokenExpiry(refreshToken)

        val member = memberRepository.findById(userId)
            ?: throw InvalidRefreshTokenException()
        member.validateMemberStatus()

        val token = tokenGenerator.generate(requireNotNull(member.memberId), roles = setOf(member.role.name))
        return RefreshTokenResult(
            accessToken = token.accessToken,
            tokenType = TOKEN_TYPE,
            expiresIn = token.expiresIn,
        )
    }

    @Transactional
    override fun logout(command: LogoutCommand) {
        accessTokenBlacklistRepository.blacklist(
            command.accessToken,
            tokenExpirationResolver.remainingTime(command.accessToken)
                .plus(LOGOUT_BLACKLIST_TTL_MARGIN),
        )
        refreshTokenRepository.deleteByUserId(command.userId)
    }

    override fun getCurrentUser(userId: Long): CurrentMemberResult {
        val member = memberRepository.findById(userId)
            ?: throw MemberNotFoundException()
        return CurrentMemberResult(
            id = requireNotNull(member.memberId),
            email = member.email,
            name = member.name,
            role = member.role,
            status = member.status,
        )
    }

    override fun getCurrentUserByEmail(email: String): CurrentMemberResult {
        val member = memberRepository.findByEmail(email)
            ?: throw MemberNotFoundException()
        return CurrentMemberResult(
            id = member.memberId ?: throw MemberNotFoundException(),
            email = member.email,
            role = member.role,
            status = member.status,
            name = member.name,
        )
    }

    private fun MemberModel.toSummary(): MemberSummary = MemberSummary(
        id = requireNotNull(memberId),
        email = email,
        name = name,
        role = role,
    )

    private companion object {
        const val TOKEN_TYPE = "Bearer"
        val LOGOUT_BLACKLIST_TTL_MARGIN: Duration = Duration.ofMinutes(1)
    }
}

private fun String.toRefreshTokenHash(): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
