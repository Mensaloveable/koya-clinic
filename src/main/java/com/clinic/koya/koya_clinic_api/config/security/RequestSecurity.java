package com.clinic.koya.koya_clinic_api.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.regex.Pattern;

public class RequestSecurity extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestSecurity.class);

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String CSRF_ATTRIBUTE = "csrf";
    private static final Pattern MATRIX_PARAMETERS = Pattern.compile(";[^/]*");

    private final byte[] key;

    public RequestSecurity(@Value("${clinic.api-key}") String key) {
        if (key.isBlank()) {
            throw new IllegalArgumentException("API_KEY must be configured");
        }
        this.key = key.getBytes(StandardCharsets.UTF_8);
    }

    public boolean authorized(HttpServletRequest request) {
        String supplied = request.getHeader(API_KEY_HEADER);
        log.info("Supplied API key: {}", supplied); //TODO: Delete this line
        return supplied != null
                && MessageDigest.isEqual(key, supplied.getBytes(StandardCharsets.UTF_8));
    }

    public static void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"unauthorized\"}");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        addSecurityHeaders(response);
        String path = sanitizePath(request.getServletPath());

        if (!isApiPath(path)) {
            if (!authorized(request)) {
                log.warn("Rejected unauthenticated API request: method={} path={} remote={}",
                        request.getMethod(), path, request.getRemoteAddr());
                unauthorized(response);
                return;
            }

        } else if (path.startsWith("/web/") && "POST".equals(request.getMethod())) {
            if (hasValidCsrfToken(request)) {
                log.warn("Rejected request with missing or invalid CSRF token: path={}", path);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().write("<p>Your form expired. <a href='/'>Return to booking</a> and try again.</p>");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    public static String csrf(HttpServletRequest request) {
        var session = request.getSession();
        if (session.getAttribute(CSRF_ATTRIBUTE) == null) {
            session.setAttribute(CSRF_ATTRIBUTE, UUID.randomUUID().toString());
        }
        return (String) session.getAttribute(CSRF_ATTRIBUTE);
    }

    private static void addSecurityHeaders(HttpServletResponse httpServletResponse) {
        httpServletResponse.setHeader("X-Content-Type-Options", "nosniff");
        httpServletResponse.setHeader("Referrer-Policy", "no-referrer");
        httpServletResponse.setHeader("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; base-uri 'none'; "
                        + "frame-ancestors 'none'; form-action 'self'");
        httpServletResponse.setHeader("Cache-Control", "no-store");
    }

    private static String sanitizePath(String path) {
        return MATRIX_PARAMETERS.matcher(path).replaceAll("");
    }

    private static boolean isApiPath(String path) {
        String cleanPath = sanitizePath(path);
        log.info("Clean path: {}", cleanPath); //TODO: Delete this line
        return cleanPath.equals("/slots")
                || cleanPath.equals("/reset")
                || cleanPath.equals("/bookings")
                || cleanPath.startsWith("/bookings/");
    }

    private static boolean hasValidCsrfToken(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String expected = session == null ? null : (String) session.getAttribute(CSRF_ATTRIBUTE);
        log.info("Expected CSRF token: {}", expected); //TODO: Delete this line
        log.info("Request CSRF token: {}", request.getParameter("csrf")); //TODO: Delete this line
        return expected != null && expected.equals(request.getParameter("csrf"));
    }
}
