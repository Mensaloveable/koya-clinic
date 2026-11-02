package com.clinic.koya.koya_clinic_api.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RequestSecurityTest {

    private static final String KEY = "secret-key";

    private final RequestSecurity security = new RequestSecurity(KEY);

    @Test
    void rejectsBlankApiKeyConfiguration() {
        assertThatThrownBy(() -> new RequestSecurity("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("API_KEY must be configured");
    }

    @Test
    void authorizedAcceptsMatchingKey() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", KEY);

        assertThat(security.authorized(request)).isTrue();
    }

    @Test
    void authorizedRejectsMissingOrWrongKey() {
        MockHttpServletRequest missing = new MockHttpServletRequest();
        MockHttpServletRequest wrong = new MockHttpServletRequest();
        wrong.addHeader("X-API-Key", "wrong");

        assertThat(security.authorized(missing)).isFalse();
        assertThat(security.authorized(wrong)).isFalse();
    }

    @Test
    void unauthorizedWritesJson401Response() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        RequestSecurity.unauthorized(response);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"unauthorized\"}");
    }

    @Test
    void doFilterRejectsApiPathsWithoutApiKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/slots");
        request.setServletPath("/slots");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        security.doFilterInternal(request, response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void doFilterAllowsPublicPagesWithoutApiKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setServletPath("/");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        security.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilterAllowsNonApiPathsWithApiKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setServletPath("/");
        request.addHeader("X-API-Key", KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        security.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilterAddsSecurityHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/slots");
        request.setServletPath("/slots");
        MockHttpServletResponse response = new MockHttpServletResponse();

        security.doFilterInternal(request, response, mock(FilterChain.class));

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("Content-Security-Policy")).contains("default-src 'self'");
    }

    @Test
    void csrfCreatesTokenOncePerSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        String first = RequestSecurity.csrf(request);
        String second = RequestSecurity.csrf(request);

        HttpSession session = request.getSession(false);
        assertThat(first).isNotBlank();
        assertThat(second).isEqualTo(first);
        assertThat(session.getAttribute("csrf")).isEqualTo(first);
    }
}
