package org.yechan.member

data class SignupCommand(
    val email: String,
    val password: String,
    val name: String,
    val role: MemberRole,
)

data class LoginCommand(
    val email: String,
    val password: String,
)

data class RefreshTokenCommand(
    val refreshToken: String,
)

data class LogoutCommand(
    val userId: Long,
    val accessToken: String,
)

data class SignupResult(
    val userId: Long,
    val email: String,
    val name: String,
    val role: MemberRole,
)

data class MemberSummary(
    val id: Long,
    val email: String,
    val name: String,
    val role: MemberRole,
)

data class LoginResult(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val user: MemberSummary,
)

data class RefreshTokenResult(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
)

data class CurrentMemberResult(
    val id: Long,
    val email: String,
    val name: String,
    val role: MemberRole,
    val status: MemberStatus,
)
