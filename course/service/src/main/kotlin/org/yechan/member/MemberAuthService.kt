package org.yechan.member

import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.yechan.AuthTokenProperties
import org.yechan.PasswordHashEncoder
import org.yechan.TokenGenerator
import org.yechan.TokenVerifier
import java.security.MessageDigest
import java.time.LocalDateTime

interface MemberAuthUseCase {
    fun signup(command: SignupCommand): SignupResult

    fun login(command: LoginCommand): LoginResult

    fun refresh(command: RefreshTokenCommand): RefreshTokenResult

    fun getCurrentUser(userId: Long): CurrentMemberResult
}

@Service
@Transactional(readOnly = true)
class MemberAuthService(
    private val memberRepository: MemberRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordHashEncoder: PasswordHashEncoder,
    private val tokenGenerator: TokenGenerator,
    private val tokenVerifier: TokenVerifier,
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
                MemberModel(
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
        val token = tokenGenerator.generate(memberId)
        refreshTokenRepository.replace(
            RefreshTokenModel(
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

        val refreshToken = refreshTokenRepository.findByTokenHash(command.refreshToken.toRefreshTokenHash())
            ?: throw InvalidRefreshTokenException()
        if (refreshToken.userId != userId) {
            throw InvalidRefreshTokenException()
        }
        if (refreshToken.expiresAt.isBefore(LocalDateTime.now())) {
            throw InvalidRefreshTokenException()
        }

        val member = memberRepository.findById(userId)
            ?: throw InvalidRefreshTokenException()
        if (member.status != MemberStatus.ACTIVE) {
            throw InvalidRefreshTokenException()
        }

        val token = tokenGenerator.generate(requireNotNull(member.memberId))
        return RefreshTokenResult(
            accessToken = token.accessToken,
            tokenType = TOKEN_TYPE,
            expiresIn = token.expiresIn,
        )
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

    private fun MemberModel.toSummary(): MemberSummary = MemberSummary(
        id = requireNotNull(memberId),
        email = email,
        name = name,
        role = role,
    )

    private companion object {
        const val TOKEN_TYPE = "Bearer"
    }
}

private fun String.toRefreshTokenHash(): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
