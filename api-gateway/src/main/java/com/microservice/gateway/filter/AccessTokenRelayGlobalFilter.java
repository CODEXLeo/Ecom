/*
 * It takes the browser's access-token cookie and converts it into: Authorization: Bearer <access-token>
 * before forwarding the request downstream.
 * It explicitly removes the original Cookie header so the access/refresh cookies aren't unnecessarily sent to Product/Order/Payment.*
 */

package com.microservice.gateway.filter;

import com.microservice.gateway.config.GatewaySecurityProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class AccessTokenRelayGlobalFilter implements GlobalFilter, Ordered {

    private final GatewaySecurityProperties properties;

    public AccessTokenRelayGlobalFilter(GatewaySecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        /*
         * User Service owns:
         *
         * - login
         * - refresh
         * - logout
         * - CSRF
         * - user account APIs
         *
         * Therefore preserve cookies on those routes.
         */
        if (isUserServiceRoute(path)) {
            return chain.filter(exchange);
        }

        var accessCookie = exchange.getRequest().getCookies().getFirst(properties.accessTokenCookieName());

        /*
         * No access-token cookie.
         *
         * An explicit Authorization header may still be used.
         */
        if (accessCookie == null || accessCookie.getValue().isBlank()) {
            return chain.filter(exchange);
        }

        /*
         * Never overwrite an explicit bearer token.
         *
         * But remove the Cookie header so refresh tokens are not
         * unnecessarily forwarded to downstream services.
         */
        if (exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION) != null) {
            return chain.filter(exchange.mutate().request(request -> request.headers(headers ->headers.remove(HttpHeaders.COOKIE))).build());
        }

        /*
         * Browser cookie becomes downstream bearer token.
         */
        var request = exchange.getRequest().mutate().headers(headers -> {
        	headers.setBearerAuth(accessCookie.getValue());
        	/*
        	 * * Prevent refresh/access cookies from being
        	 * * leaked to Product/Order/Payment.
        	 * */
        	headers.remove(HttpHeaders.COOKIE);}).build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    private boolean isUserServiceRoute(String path) {
        return path.startsWith("/api/v1/auth/") || path.startsWith("/api/v1/users/") || path.startsWith("/api/v1/admin/");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}