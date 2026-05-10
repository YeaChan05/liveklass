package org.yechan.member

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority

class MemberAuthenticationProvider(
    private val memberAuthUseCase: MemberAuthUseCase,
) : AuthenticationProvider {
    override fun authenticate(authentication: Authentication): Authentication {
        val userId =
            authentication.name.toLongOrNull()
                ?: throw BadCredentialsException("Invalid token subject")

        return memberAuthUseCase.getCurrentUser(userId).toToken()
    }

    override fun supports(authentication: Class<*>): Boolean = CustomMemberAuthenticationToken::class.java.isAssignableFrom(authentication)
}

private fun CurrentMemberResult.toToken(): Authentication = CustomMemberAuthenticationToken(
    userId = id,
    authorities = role.toAuthorities(),
)

private fun MemberRole.toAuthorities(): List<GrantedAuthority> = when (this) {
    MemberRole.CREATOR -> listOf(
        SimpleGrantedAuthority("ROLE_CREATOR"),
    )

    MemberRole.CLASSMATE -> listOf(
        SimpleGrantedAuthority("ROLE_CLASSMATE"),
    )
}

class CustomMemberAuthenticationToken(
    private val userId: Long,
    authorities: Collection<GrantedAuthority>,
) : AbstractAuthenticationToken(authorities) {
    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any? = null

    override fun getPrincipal(): Any = userId

    override fun getName(): String = userId.toString()
}
