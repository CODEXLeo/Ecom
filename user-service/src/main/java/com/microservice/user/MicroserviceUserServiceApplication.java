package com.microservice.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

import com.microservice.user.security.auth.AuthenticationProperties;
import com.microservice.user.security.bootstrap.BootstrapAdminProperties;
import com.microservice.user.security.cookie.AuthCookieProperties;
import com.microservice.user.security.jwt.JwtProperties;

@EnableDiscoveryClient
@EnableConfigurationProperties({JwtProperties.class, AuthCookieProperties.class,
	AuthenticationProperties.class, BootstrapAdminProperties.class}) // Create an instance of this class and manage it as configuration.
@SpringBootApplication
public class MicroserviceUserServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(MicroserviceUserServiceApplication.class, args);
	}

}
