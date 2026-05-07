package org.yechan.member

import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.yechan.LoginUserId
import org.yechan.member.dto.CurrentMemberResponse
import org.yechan.member.dto.LoginRequest
import org.yechan.member.dto.LoginResponse
import org.yechan.member.dto.RefreshTokenRequest
import org.yechan.member.dto.RefreshTokenResponse
import org.yechan.member.dto.SignupRequest
import org.yechan.member.dto.SignupResponse

@RestController
@RequestMapping("/api/auth", version = "v1")
class MemberAuthController(
    private val memberAuthUseCase: MemberAuthUseCase,
) {
    @PostMapping("/signup")
    fun signup(
        @RequestBody @Valid request: SignupRequest,
    ): SignupResponse {
        val result = memberAuthUseCase.signup(request.toCommand())
        return SignupResponse.from(result)
    }

    @PostMapping("/login")
    fun login(
        @RequestBody @Valid request: LoginRequest,
    ): LoginResponse {
        val result = memberAuthUseCase.login(LoginCommand(request.email.trim(), request.password))
        return LoginResponse.from(result)
    }

    @PostMapping("/token/refresh")
    fun refresh(
        @RequestBody @Valid request: RefreshTokenRequest,
    ): RefreshTokenResponse {
        val result = memberAuthUseCase.refresh(RefreshTokenCommand(request.refreshToken))
        return RefreshTokenResponse.from(result)
    }

    @PostMapping("/logout")
    fun logout(
        @LoginUserId userId: Long,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String,
    ): ResponseEntity<Void> {
        memberAuthUseCase.logout(LogoutCommand(userId, authorization.toBearerToken()))
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/me")
    fun me(
        @LoginUserId userId: Long,
    ): CurrentMemberResponse {
        val result = memberAuthUseCase.getCurrentUser(userId)
        return CurrentMemberResponse.from(result)
    }

    private fun String.toBearerToken(): String = removePrefix(BEARER_PREFIX)

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
