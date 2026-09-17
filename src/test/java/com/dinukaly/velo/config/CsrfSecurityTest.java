package com.dinukaly.velo.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CsrfSecurityTest {

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private FilterChain filterChain;

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        BrowserOriginProperties browserOriginProperties = new BrowserOriginProperties();
        browserOriginProperties.setAllowedOrigins(List.of("https://app.velo.example"));

        CookieProperties cookieProperties = new CookieProperties();
        cookieProperties.setSecure(true);
        cookieProperties.setSameSite(CookieProperties.SameSitePolicy.NONE);

        securityConfig = new SecurityConfig(
                userDetailsService,
                passwordEncoder,
                browserOriginProperties,
                cookieProperties);
    }

    @Test
    void csrfCookieUsesConfiguredSecurityAttributes() {
        CookieCsrfTokenRepository repository = securityConfig.csrfTokenRepository();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/csrf");
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveToken(repository.generateToken(request), request, response);

        jakarta.servlet.http.Cookie csrfCookie = response.getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);
        assertEquals("/", csrfCookie.getPath());
        assertTrue(csrfCookie.getSecure());
        assertTrue(csrfCookie.isHttpOnly());
        assertEquals("None", csrfCookie.getAttribute("SameSite"));
    }

    @Test
    void unsafeRequestWithoutTokenIsRejected() throws Exception {
        CookieCsrfTokenRepository repository = securityConfig.csrfTokenRepository();
        CsrfFilter csrfFilter = new CsrfFilter(repository);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/signin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        csrfFilter.doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
        verifyNoInteractions(filterChain);
    }

    @Test
    void unsafeRequestWithCookieAndMatchingHeaderIsAccepted() throws Exception {
        CookieCsrfTokenRepository repository = securityConfig.csrfTokenRepository();
        MockHttpServletRequest bootstrapRequest = new MockHttpServletRequest("GET", "/api/v1/auth/csrf");
        MockHttpServletResponse bootstrapResponse = new MockHttpServletResponse();
        CsrfToken token = repository.generateToken(bootstrapRequest);
        repository.saveToken(token, bootstrapRequest, bootstrapResponse);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/signin");
        request.setCookies(bootstrapResponse.getCookie("XSRF-TOKEN"));
        request.addHeader(token.getHeaderName(), token.getToken());
        MockHttpServletResponse response = new MockHttpServletResponse();
        CsrfFilter csrfFilter = new CsrfFilter(repository);
        csrfFilter.setRequestHandler(new CsrfTokenRequestAttributeHandler());

        csrfFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
