package com.microservice.user.security.cookie;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;

@Service
public class CookieService {

    private final AuthCookieProperties properties;

    public CookieService(AuthCookieProperties properties) {
        this.properties = properties;
    }

    public void addAccessTokenCookie(HttpServletResponse response, String accessToken, Duration expiration) {
        ResponseCookie cookie = ResponseCookie.from(properties.getAccessTokenName(), accessToken)
                .httpOnly(true)
                .secure(properties.isSecure())
                .path("/")
                .sameSite(properties.getSameSite())
                .maxAge(expiration)
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }

    public void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {

        ResponseCookie cookie = ResponseCookie.from(properties.getRefreshTokenName(), refreshToken)
                .httpOnly(true)
                .secure(properties.isSecure())
                .path("/")
                .sameSite(properties.getSameSite())
                .maxAge(properties.getRefreshTokenExpiration())
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }

    public void clearAuthenticationCookies(HttpServletResponse response) {
        clearCookie(response, properties.getAccessTokenName());
        clearCookie(response, properties.getRefreshTokenName());
    }

    private void clearCookie(HttpServletResponse response, String cookieName) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(properties.isSecure())
                .path("/")
                .sameSite(properties.getSameSite())
                .maxAge(Duration.ZERO)
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }
}