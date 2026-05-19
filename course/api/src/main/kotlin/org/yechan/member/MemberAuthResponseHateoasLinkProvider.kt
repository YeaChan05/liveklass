package org.yechan.member

import org.springframework.hateoas.Link
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo
import org.yechan.ApiHateoasLinkProvider

class MemberAuthResponseHateoasLinkProvider : ApiHateoasLinkProvider {
    override fun supports(body: Any): Boolean = body is SignupResponse ||
        body is LoginResponse ||
        body is RefreshTokenResponse ||
        body is CurrentMemberResponse

    override fun links(body: Any): Iterable<Link> = when (body) {
        is SignupResponse -> listOf(authLink("login").withRel("login"))
        is LoginResponse -> authenticatedLinks()
        is RefreshTokenResponse -> listOf(
            authLink("me").withRel("me"),
            authLink("token").slash("refresh").withRel("refresh"),
        )
        is CurrentMemberResponse -> listOf(
            authLink("me").withSelfRel(),
            authLink("logout").withRel("logout"),
        )
        else -> emptyList()
    }
}

private fun authenticatedLinks(): List<Link> = listOf(
    authLink("me").withRel("me"),
    authLink("token").slash("refresh").withRel("refresh"),
    authLink("logout").withRel("logout"),
)

private fun authLink(path: String) = linkTo(MemberAuthController::class.java).slash(path)
