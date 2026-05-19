package org.yechan;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NullUnmarked;

@NullUnmarked
@FunctionalInterface
public interface TokenParser {
    String parse(HttpServletRequest request);
}
