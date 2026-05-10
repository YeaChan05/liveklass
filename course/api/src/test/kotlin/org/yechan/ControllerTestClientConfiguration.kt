package org.yechan

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.ApiVersionInserter
import org.springframework.web.context.WebApplicationContext
import org.yechan.member.CurrentMemberResult
import org.yechan.member.LoginCommand
import org.yechan.member.LoginResult
import org.yechan.member.LogoutCommand
import org.yechan.member.MemberAuthUseCase
import org.yechan.member.MemberRole
import org.yechan.member.MemberStatus
import org.yechan.member.RefreshTokenCommand
import org.yechan.member.RefreshTokenResult
import org.yechan.member.SignupCommand
import org.yechan.member.SignupResult

@TestConfiguration
class ControllerTestClientConfiguration {
    @Bean
    fun restTestClient(context: WebApplicationContext): RestTestClient {
        val mockMvc =
            MockMvcBuilders.webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()

        return RestTestClient.bindTo(mockMvc)
            .apiVersionInserter(ApiVersionInserter.useHeader("X-API-Version"))
            .defaultHeader(HttpHeaders.ACCEPT, "application/json")
            .build()
    }
}

@TestConfiguration
class TestMemberAuthUseCaseConfiguration {
    @Bean
    fun memberAuthUseCase(): MemberAuthUseCase = TestMemberAuthUseCase()
}

class TestMemberAuthUseCase : MemberAuthUseCase {
    override fun signup(command: SignupCommand): SignupResult = throw UnsupportedOperationException()

    override fun login(command: LoginCommand): LoginResult = throw UnsupportedOperationException()

    override fun refresh(command: RefreshTokenCommand): RefreshTokenResult = throw UnsupportedOperationException()

    override fun logout(command: LogoutCommand) {
    }

    override fun getCurrentUser(userId: Long): CurrentMemberResult = CurrentMemberResult(
        id = userId,
        email = "user$userId@test.com",
        name = "user$userId",
        role = roleOf(userId),
        status = MemberStatus.ACTIVE,
    )

    override fun getCurrentUserByEmail(email: String): CurrentMemberResult {
        val userId = email.filter(Char::isDigit).toLongOrNull() ?: 1L

        return CurrentMemberResult(
            id = userId,
            email = email,
            name = "user$userId",
            role = roleOf(userId),
            status = MemberStatus.ACTIVE,
        )
    }

    private fun roleOf(userId: Long): MemberRole = when (userId) {
        1L, 3L -> MemberRole.CREATOR
        2L -> MemberRole.CLASSMATE
        else -> MemberRole.CLASSMATE
    }
}
