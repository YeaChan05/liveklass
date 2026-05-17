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

interface MemberRegistrationHandler {
    fun signup(command: SignupCommand): SignupResult
}

interface MemberSessionHandler {
    fun login(command: LoginCommand): LoginResult

    fun refresh(command: RefreshTokenCommand): RefreshTokenResult

    fun logout(command: LogoutCommand)
}

interface MemberCurrentMemberHandler {
    fun getCurrentUser(userId: Long): CurrentMemberResult

    fun getCurrentUserByEmail(email: String): CurrentMemberResult
}

class MemberAuthService(
    private val registrationHandler: MemberRegistrationHandler,
    private val sessionHandler: MemberSessionHandler,
    private val currentMemberHandler: MemberCurrentMemberHandler,
) : MemberAuthUseCase {
    override fun signup(command: SignupCommand): SignupResult = registrationHandler.signup(command)

    override fun login(command: LoginCommand): LoginResult = sessionHandler.login(command)

    override fun refresh(command: RefreshTokenCommand): RefreshTokenResult = sessionHandler.refresh(command)

    override fun logout(command: LogoutCommand) {
        sessionHandler.logout(command)
    }

    override fun getCurrentUser(userId: Long): CurrentMemberResult = currentMemberHandler.getCurrentUser(userId)

    override fun getCurrentUserByEmail(email: String): CurrentMemberResult = currentMemberHandler.getCurrentUserByEmail(email)
}

@Transactional(readOnly = true)
class MemberRegistrationProcessor(
    private val memberRepository: MemberRepository,
    private val passwordHashEncoder: PasswordHashEncoder,
) : MemberRegistrationHandler {
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
}

@Transactional(readOnly = true)
class MemberSessionProcessor(
    private val memberRepository: MemberRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordHashEncoder: PasswordHashEncoder,
    private val tokenGenerator: TokenGenerator,
    private val tokenVerifier: TokenVerifier,
    private val tokenExpirationResolver: TokenExpirationResolver,
    private val accessTokenBlacklistRepository: AccessTokenBlacklistRepository,
    private val authTokenProperties: AuthTokenProperties,
) : MemberSessionHandler {
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

@Transactional(readOnly = true)
class MemberCurrentMemberProcessor(
    private val memberRepository: MemberRepository,
) : MemberCurrentMemberHandler {
    override fun getCurrentUser(userId: Long): CurrentMemberResult {
        val member = memberRepository.findById(userId)
            ?: throw MemberNotFoundException()
        return member.toCurrentMemberResult()
    }

    override fun getCurrentUserByEmail(email: String): CurrentMemberResult {
        val member = memberRepository.findByEmail(email)
            ?: throw MemberNotFoundException()
        return member.toCurrentMemberResult()
    }

    private fun MemberModel.toCurrentMemberResult(): CurrentMemberResult = CurrentMemberResult(
        id = memberId ?: throw MemberNotFoundException(),
        email = email,
        role = role,
        status = status,
        name = name,
    )
}

private fun String.toRefreshTokenHash(): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
