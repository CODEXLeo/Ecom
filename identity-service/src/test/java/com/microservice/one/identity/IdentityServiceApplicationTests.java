package com.microservice.one.identity;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "spring.sql.init.mode=never"
})
@Testcontainers
class IdentityServiceApplicationTests {

    @Container
    static MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("identity_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)
            .withTmpFs(Map.of("/var/lib/mysql", "rw,noexec,nosuid,size=256m")) // Faster in-memory temp
            .withStartupTimeout(Duration.ofMinutes(2));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        
        // JWT secrets remain unchanged
        registry.add("jwt.access-secret", () -> "ThisIsAVeryLongAccessSecretKeyForTesting123456789");
        registry.add("jwt.refresh-secret", () -> "ThisIsAVeryLongRefreshSecretKeyForTesting123456789");
        registry.add("jwt.access-expiration", () -> "900000");
        registry.add("jwt.refresh-expiration", () -> "604800000");
    }

    @Test
    void contextLoads() {
    }
}