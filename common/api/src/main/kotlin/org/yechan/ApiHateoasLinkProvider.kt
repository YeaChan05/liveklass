package org.yechan

import org.springframework.hateoas.Link

interface ApiHateoasLinkProvider {
    fun supports(body: Any): Boolean

    fun links(body: Any): Iterable<Link>
}
