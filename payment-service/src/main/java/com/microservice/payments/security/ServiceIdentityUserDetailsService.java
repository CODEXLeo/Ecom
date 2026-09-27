package com.microservice.payments.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class ServiceIdentityUserDetailsService
        implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(
            String username
    ) {
        if ("order-service".equals(
                username
        )) {

            return User.withUsername(
                            "order-service"
                    )
                    .password("{noop}unused")
                    .authorities(
                            "ROLE_SERVICE_ORDER"
                    )
                    .build();
        }

        throw new UsernameNotFoundException(
                "Unknown service identity: "
                        + username
        );
    }
}