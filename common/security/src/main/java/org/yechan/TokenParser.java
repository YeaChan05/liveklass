package org.yechan;

import jakarta.servlet.http.HttpServletRequest;

@FunctionalInterface
public interface TokenParser {
    String parse(HttpServletRequest request);
}
