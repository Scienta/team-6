package no.scienta.cfp.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * One log line per request: method, path, query, status, duration, and the (truncated) request and
 * response bodies. The harness's checks are invisible otherwise. /health is skipped to keep the noise down.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("http");
    private static final int MAX_BODY = 1000;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("/health".equals(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        ContentCachingRequestWrapper req = new ContentCachingRequestWrapper(request, MAX_BODY);
        ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(req, res);
        } finally {
            long ms = System.currentTimeMillis() - start;
            String query = request.getQueryString() == null ? "" : "?" + request.getQueryString();
            log.info("{} {}{} -> {} ({} ms) req={} res={}", request.getMethod(), request.getRequestURI(), query,
                    res.getStatus(), ms, body(req.getContentAsByteArray()), body(res.getContentAsByteArray()));
            res.copyBodyToResponse();
        }
    }

    private static String body(byte[] bytes) {
        if (bytes.length == 0) {
            return "-";
        }
        String s = new String(bytes, 0, Math.min(bytes.length, MAX_BODY), StandardCharsets.UTF_8).replace('\n', ' ');
        return bytes.length > MAX_BODY ? s + "...(" + bytes.length + " bytes)" : s;
    }
}
