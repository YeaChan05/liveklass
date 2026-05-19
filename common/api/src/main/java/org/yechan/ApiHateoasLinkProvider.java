package org.yechan;

import org.springframework.hateoas.Link;

public interface ApiHateoasLinkProvider {
    boolean supports(Object body);

    Iterable<Link> links(Object body);
}
