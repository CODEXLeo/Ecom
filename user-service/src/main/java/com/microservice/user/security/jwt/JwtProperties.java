package com.microservice.user.security.jwt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;


/*Contains
 * issuer
 * access-token-expiration
 * private-key-path
 * public-key-path
*/


@ConfigurationProperties(prefix = "security.jwt") // This class represents properties beginning with security.jwt
public class JwtProperties {

    private String issuer;
    private Duration accessTokenExpiration;
    private String privateKeyPath;
    private String publicKeyPath;
    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Duration getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    public void setAccessTokenExpiration(Duration accessTokenExpiration) {
        this.accessTokenExpiration = accessTokenExpiration;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public String getPublicKeyPath() {
        return publicKeyPath;
    }

    public void setPublicKeyPath(String publicKeyPath) {
        this.publicKeyPath = publicKeyPath;
    }
}