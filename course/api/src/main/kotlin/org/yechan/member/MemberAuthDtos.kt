package org.yechan.member

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class SignupRequest(
    @field:NotBlank(message = "올바른 이메일 형식이 아닙니다.")
    @field:Email(message = "올바른 이메일 형식이 아닙니다.")
    @field:Size(max = 255, message = "올바른 이메일 형식이 아닙니다.")
    val email: String = "",
    @field:NotBlank(message = "비밀번호는 8자 이상 64자 이하로 입력해야 합니다.")
    @field:Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하로 입력해야 합니다.")
    @field:Pattern(
        regexp = "^(?!\\s+$)(?=.*[A-Za-z].*|.*\\d.*.*[^A-Za-z0-9].*|.*[A-Za-z].*.*\\d.*|.*[A-Za-z].*.*[^A-Za-z0-9].*).+$",
        message = "비밀번호는 8자 이상 64자 이하로 입력해야 합니다.",
    )
    val password: String = "",
    @field:NotBlank(message = "이름은 2자 이상 30자 이하로 입력해야 합니다.")
    @field:Size(min = 2, max = 30, message = "이름은 2자 이상 30자 이하로 입력해야 합니다.")
    val name: String = "",
    @field:NotBlank(message = "가입 가능한 권한이 아닙니다.")
    @field:Pattern(regexp = "CREATOR|CLASSMATE", message = "가입 가능한 권한이 아닙니다.")
    val role: String = "",
) {
    fun toCommand(): SignupCommand = SignupCommand(
        email = email.trim(),
        password = password,
        name = name.trim(),
        role = MemberRole.valueOf(role),
    )
}

data class LoginRequest(
    @field:NotBlank(message = LOGIN_FAILED_MESSAGE)
    @field:Email(message = LOGIN_FAILED_MESSAGE)
    val email: String = "",
    @field:NotBlank(message = LOGIN_FAILED_MESSAGE)
    val password: String = "",
)

data class RefreshTokenRequest(
    @field:NotBlank(message = INVALID_REFRESH_TOKEN_MESSAGE)
    val refreshToken: String = "",
)

data class SignupResponse(
    val userId: Long,
    val email: String,
    val name: String,
    val role: MemberRole,
) {
    companion object {
        fun from(result: SignupResult) = SignupResponse(
            userId = result.userId,
            email = result.email,
            name = result.name,
            role = result.role,
        )
    }
}

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val user: MemberSummary,
) {
    companion object {
        fun from(result: LoginResult) = LoginResponse(
            accessToken = result.accessToken,
            refreshToken = result.refreshToken,
            tokenType = result.tokenType,
            expiresIn = result.expiresIn,
            user = result.user,
        )
    }
}

data class RefreshTokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
) {
    companion object {
        fun from(result: RefreshTokenResult) = RefreshTokenResponse(
            accessToken = result.accessToken,
            tokenType = result.tokenType,
            expiresIn = result.expiresIn,
        )
    }
}

data class CurrentMemberResponse(
    val id: Long,
    val email: String,
    val name: String,
    val role: MemberRole,
    val status: MemberStatus,
) {
    companion object {
        fun from(result: CurrentMemberResult) = CurrentMemberResponse(
            id = result.id,
            email = result.email,
            name = result.name,
            role = result.role,
            status = result.status,
        )
    }
}

private const val LOGIN_FAILED_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다."
private const val INVALID_REFRESH_TOKEN_MESSAGE = "유효하지 않은 Refresh Token입니다."
