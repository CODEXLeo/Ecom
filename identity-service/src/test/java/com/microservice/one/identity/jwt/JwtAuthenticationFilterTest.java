package com.microservice.one.identity.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.microservice.one.identity.constants.SecurityConstants;

import jakarta.servlet.ServletException;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String EMAIL =
            "swata@example.com";

    private static final String TOKEN =
            "valid-access-token";

    @Mock
    private JwtService jwtService;

    @Mock
    private UserDetailsService userDetailsService;

    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private MockHttpServletRequest request;

    private MockHttpServletResponse response;

    private MockFilterChain filterChain;

    private UserDetails userDetails;

    @BeforeEach
    void setUp() {

        jwtAuthenticationFilter =
                new JwtAuthenticationFilter(
                        jwtService,
                        userDetailsService);

        request =
                new MockHttpServletRequest();

        response =
                new MockHttpServletResponse();

        filterChain =
                new MockFilterChain();

        userDetails =
                User.withUsername(EMAIL)
                        .password("password")
                        .roles("USER")
                        .build();

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {

        SecurityContextHolder.clearContext();
    }

    /*
     * ============================================================
     * AUTHENTICATION ENDPOINT
     * ============================================================
     */

    @Test
    void authenticationEndpoint_shouldSkipJwtProcessing()
            throws ServletException, IOException {

        request.setRequestURI(
                "/api/v1/auth/login");

        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain);

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication());

        verify(jwtService, never())
                .canParseAccessToken(TOKEN);
    }

    /*
     * ============================================================
     * MISSING AUTHORIZATION HEADER
     * ============================================================
     */

    @Test
    void requestWithoutAuthorizationHeader_shouldContinueUnauthenticated()
            throws ServletException, IOException {

        request.setRequestURI(
                "/api/v1/users/me");

        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain);

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication());

        verify(jwtService, never())
                .canParseAccessToken(TOKEN);
    }

    /*
     * ============================================================
     * INVALID AUTHORIZATION HEADER
     * ============================================================
     */

    @Test
    void requestWithInvalidAuthorizationHeader_shouldContinueUnauthenticated()
            throws ServletException, IOException {

        request.setRequestURI(
                "/api/v1/users/me");

        request.addHeader(
                SecurityConstants.AUTHORIZATION_HEADER,
                "Basic abc123");

        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain);

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication());

        verify(jwtService, never())
                .canParseAccessToken(TOKEN);
    }

    /*
     * ============================================================
     * INVALID JWT
     * ============================================================
     */

    @Test
    void invalidAccessToken_shouldContinueUnauthenticated()
            throws ServletException, IOException {

        request.setRequestURI(
                "/api/v1/users/me");

        request.addHeader(
                SecurityConstants.AUTHORIZATION_HEADER,
                SecurityConstants.TOKEN_PREFIX
                        + TOKEN);

        when(jwtService.canParseAccessToken(TOKEN))
                .thenReturn(false);

        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain);

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication());

        verify(jwtService)
                .canParseAccessToken(TOKEN);

        verify(jwtService, never())
                .extractEmail(TOKEN);

        verify(userDetailsService, never())
                .loadUserByUsername(EMAIL);
    }

    /*
     * ============================================================
     * VALID JWT
     * ============================================================
     */

    @Test
    void validAccessToken_shouldAuthenticateUser()
            throws ServletException, IOException {

        request.setRequestURI(
                "/api/v1/users/me");

        request.addHeader(
                SecurityConstants.AUTHORIZATION_HEADER,
                SecurityConstants.TOKEN_PREFIX
                        + TOKEN);

        when(jwtService.canParseAccessToken(TOKEN))
                .thenReturn(true);

        when(jwtService.extractEmail(TOKEN))
                .thenReturn(EMAIL);

        when(userDetailsService.loadUserByUsername(EMAIL))
                .thenReturn(userDetails);

        when(jwtService.isAccessTokenValid(
                TOKEN,
                userDetails))
                .thenReturn(true);

        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain);

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        assertNotNull(authentication);

        assertTrueAuthentication(
                authentication);

        assertSame(
                userDetails,
                authentication.getPrincipal());

        /*
         * Compare authorities by contents rather than concrete
         * collection implementation (Set vs List).
         */
        assertEquals(
                List.copyOf(
                        userDetails.getAuthorities()),
                List.copyOf(
                        authentication.getAuthorities()));

        verify(jwtService)
                .canParseAccessToken(TOKEN);

        verify(jwtService)
                .extractEmail(TOKEN);

        verify(userDetailsService)
                .loadUserByUsername(EMAIL);

        verify(jwtService)
                .isAccessTokenValid(
                        TOKEN,
                        userDetails);
    }

    /*
     * ============================================================
     * INVALID USER DETAILS
     * ============================================================
     */

    @Test
    void accessTokenWithInvalidUserDetails_shouldNotAuthenticateUser()
            throws ServletException, IOException {

        request.setRequestURI(
                "/api/v1/users/me");

        request.addHeader(
                SecurityConstants.AUTHORIZATION_HEADER,
                SecurityConstants.TOKEN_PREFIX
                        + TOKEN);

        when(jwtService.canParseAccessToken(TOKEN))
                .thenReturn(true);

        when(jwtService.extractEmail(TOKEN))
                .thenReturn(EMAIL);

        when(userDetailsService.loadUserByUsername(EMAIL))
                .thenReturn(userDetails);

        when(jwtService.isAccessTokenValid(
                TOKEN,
                userDetails))
                .thenReturn(false);

        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain);

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication());

        verify(jwtService)
                .isAccessTokenValid(
                        TOKEN,
                        userDetails);
    }

    /*
     * ============================================================
     * AUTHENTICATION ALREADY EXISTS
     * ============================================================
     */

    @Test
    void existingAuthentication_shouldNotBeOverwritten()
            throws ServletException, IOException {

        request.setRequestURI(
                "/api/v1/users/me");

        request.addHeader(
                SecurityConstants.AUTHORIZATION_HEADER,
                SecurityConstants.TOKEN_PREFIX
                        + TOKEN);

        Authentication existingAuthentication =
                new UsernamePasswordAuthenticationToken(
                        "existing-user",
                        null,
                        List.of());

        SecurityContextHolder
                .getContext()
                .setAuthentication(
                        existingAuthentication);

        when(jwtService.canParseAccessToken(TOKEN))
                .thenReturn(true);

        when(jwtService.extractEmail(TOKEN))
                .thenReturn(EMAIL);

        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain);

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        assertSame(
                existingAuthentication,
                authentication);

        verify(userDetailsService, never())
                .loadUserByUsername(EMAIL);

        verify(jwtService, never())
                .isAccessTokenValid(
                        TOKEN,
                        userDetails);
    }

    /*
     * ============================================================
     * EMAIL EXTRACTION FAILURE
     * ============================================================
     */

    @Test
    void tokenWithoutEmail_shouldContinueUnauthenticated()
            throws ServletException, IOException {

        request.setRequestURI(
                "/api/v1/users/me");

        request.addHeader(
                SecurityConstants.AUTHORIZATION_HEADER,
                SecurityConstants.TOKEN_PREFIX
                        + TOKEN);

        when(jwtService.canParseAccessToken(TOKEN))
                .thenReturn(true);

        when(jwtService.extractEmail(TOKEN))
                .thenReturn(null);

        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain);

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication());

        verify(userDetailsService, never())
                .loadUserByUsername(EMAIL);
    }

    /*
     * ============================================================
     * UNKNOWN USER
     * ============================================================
     */

    @Test
    void validAccessTokenForUnknownUser_shouldPropagateUserNotFoundException()
            throws ServletException, IOException {

        request.setRequestURI(
                "/api/v1/users/me");

        request.addHeader(
                SecurityConstants.AUTHORIZATION_HEADER,
                SecurityConstants.TOKEN_PREFIX
                        + TOKEN);

        when(jwtService.canParseAccessToken(TOKEN))
                .thenReturn(true);

        when(jwtService.extractEmail(TOKEN))
                .thenReturn(EMAIL);

        when(userDetailsService.loadUserByUsername(EMAIL))
                .thenThrow(
                        new UsernameNotFoundException(
                                "User not found"));

        assertThrows(
                UsernameNotFoundException.class,
                () -> jwtAuthenticationFilter.doFilter(
                        request,
                        response,
                        filterChain));

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication());

        verify(userDetailsService)
                .loadUserByUsername(EMAIL);
    }

    /*
     * ============================================================
     * HELPERS
     * ============================================================
     */

    private void assertTrueAuthentication(
            Authentication authentication) {

        assertEquals(
                EMAIL,
                authentication.getName());

        assertEquals(
                true,
                authentication.isAuthenticated());
    }
}