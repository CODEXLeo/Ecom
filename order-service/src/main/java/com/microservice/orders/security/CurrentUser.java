package com.microservice.orders.security;

import java.util.UUID;

public interface CurrentUser {

    UUID userId();

    String role();

    boolean isAdmin();
}