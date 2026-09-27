package com.microservice.one.identity.jwt;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.microservice.one.identity.constants.SecurityConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    JwtAuthenticationFilter.class);

    private final JwtService jwtService;

    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(

            JwtService jwtService,

            UserDetailsService userDetailsService) {

        this.jwtService = jwtService;

        this.userDetailsService = userDetailsService;

    }

    @Override
    protected void doFilterInternal(

            HttpServletRequest request,

            HttpServletResponse response,

            FilterChain filterChain)

            throws ServletException, IOException {

        String requestUri =
                request.getRequestURI();

        /*
         * Skip authentication endpoints.
         */

        if (requestUri.startsWith("/api/v1/auth")) {

            filterChain.doFilter(
                    request,
                    response);

            return;

        }

        final String authorizationHeader =
                request.getHeader(
                        SecurityConstants.AUTHORIZATION_HEADER);

        if (authorizationHeader == null
                || !authorizationHeader.startsWith(
                        SecurityConstants.TOKEN_PREFIX)) {

            filterChain.doFilter(
                    request,
                    response);

            return;

        }

        final String jwtToken =
                authorizationHeader.substring(
                        SecurityConstants.TOKEN_PREFIX.length());

        if (!jwtService.canParseAccessToken(jwtToken)) {

            filterChain.doFilter(
                    request,
                    response);

            return;

        }

        final String email =
                jwtService.extractEmail(jwtToken);

        if (email == null
                || SecurityContextHolder
                        .getContext()
                        .getAuthentication() != null) {

            filterChain.doFilter(
                    request,
                    response);

            return;

        }

        UserDetails userDetails =
                userDetailsService.loadUserByUsername(
                        email);

        if (jwtService.isAccessTokenValid(

                jwtToken,

                userDetails)) {

            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(

                            userDetails,

                            null,

                            userDetails.getAuthorities());

            authenticationToken.setDetails(

                    new WebAuthenticationDetailsSource()

                            .buildDetails(request));

            SecurityContextHolder

                    .getContext()

                    .setAuthentication(authenticationToken);

            LOGGER.debug(
                    "Authenticated {}",
                    email);

        }

        filterChain.doFilter(
                request,
                response);

    }

}