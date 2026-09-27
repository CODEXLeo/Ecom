package com.microservice.one.identity.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.microservice.one.identity.entity.User;
import com.microservice.one.identity.enums.Role;

import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@Testcontainers
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
class UserRepositoryTest {

	@Container
	static final MySQLContainer<?> MYSQL =
	        new MySQLContainer<>("mysql:8.0")
	                .withDatabaseName("identity_test")
	                .withUsername("test")
	                .withPassword("test")
	                .withTmpFs(Map.of(
	                        "/var/lib/mysql",
	                        "rw,noexec,nosuid,size=256m"))
	                .withStartupTimeout(Duration.ofMinutes(2));
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @DynamicPropertySource
    static void configureProperties(
            DynamicPropertyRegistry registry) {

        registry.add(
                "spring.datasource.url",
                MYSQL::getJdbcUrl);

        registry.add(
                "spring.datasource.username",
                MYSQL::getUsername);

        registry.add(
                "spring.datasource.password",
                MYSQL::getPassword);
    }

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void saveUser_shouldPersistUser() {

        User user = createUser(
                "john@example.com",
                Role.ROLE_USER);

        User savedUser = userRepository.saveAndFlush(user);

        assertThat(savedUser.getUserId())
                .isNotNull();

        assertThat(savedUser.getFirstName())
                .isEqualTo("John");

        assertThat(savedUser.getLastName())
                .isEqualTo("Doe");

        assertThat(savedUser.getEmail())
                .isEqualTo("john@example.com");

        assertThat(savedUser.getPasswordHash())
                .isEqualTo("hashed-password");

        assertThat(savedUser.getRole())
                .isEqualTo(Role.ROLE_USER);

        assertThat(savedUser.isEnabled())
                .isTrue();

        assertThat(savedUser.isAccountNonLocked())
                .isTrue();

        assertThat(savedUser.getCreatedAt())
                .isNotNull();

        assertThat(savedUser.getUpdatedAt())
                .isNotNull();
    }

    @Test
    void findByEmail_shouldReturnUser() {

        User user = createUser(
                "john@example.com",
                Role.ROLE_USER);

        userRepository.saveAndFlush(user);

        Optional<User> result =
                userRepository.findByEmail(
                        "john@example.com");

        assertThat(result)
                .isPresent();

        assertThat(result.get().getEmail())
                .isEqualTo("john@example.com");

        assertThat(result.get().getFirstName())
                .isEqualTo("John");

        assertThat(result.get().getLastName())
                .isEqualTo("Doe");

        assertThat(result.get().getRole())
                .isEqualTo(Role.ROLE_USER);
    }

    @Test
    void findByEmail_shouldReturnEmptyForUnknownEmail() {

        Optional<User> result =
                userRepository.findByEmail(
                        "unknown@example.com");

        assertThat(result)
                .isEmpty();
    }

    @Test
    void existsByEmail_shouldReturnTrueForExistingEmail() {

        User user = createUser(
                "john@example.com",
                Role.ROLE_USER);

        userRepository.saveAndFlush(user);

        assertThat(
                userRepository.existsByEmail(
                        "john@example.com"))
                .isTrue();
    }

    @Test
    void existsByEmail_shouldReturnFalseForUnknownEmail() {

        assertThat(
                userRepository.existsByEmail(
                        "unknown@example.com"))
                .isFalse();
    }

    @Test
    void saveUser_shouldPersistRoleAsEnumString() {

        User user = createUser(
                "admin@example.com",
                Role.ROLE_ADMIN);

        User savedUser =
                userRepository.saveAndFlush(user);

        entityManager.clear();

        User reloadedUser =
                userRepository.findById(
                        savedUser.getUserId())
                        .orElseThrow();

        assertThat(reloadedUser.getRole())
                .isEqualTo(Role.ROLE_ADMIN);
    }

    @Test
    void saveUser_shouldRespectDefaultAccountFlags() {

        User user = createUser(
                "john@example.com",
                Role.ROLE_USER);

        assertThat(user.isEnabled())
                .isTrue();

        assertThat(user.isAccountNonLocked())
                .isTrue();

        User savedUser =
                userRepository.saveAndFlush(user);

        entityManager.clear();

        User reloadedUser =
                userRepository.findById(
                        savedUser.getUserId())
                        .orElseThrow();

        assertThat(reloadedUser.isEnabled())
                .isTrue();

        assertThat(reloadedUser.isAccountNonLocked())
                .isTrue();
    }

    @Test
    void saveUser_shouldGenerateUniqueIds() {

        User firstUser = createUser(
                "first@example.com",
                Role.ROLE_USER);

        User secondUser = createUser(
                "second@example.com",
                Role.ROLE_USER);

        User savedFirst =
                userRepository.saveAndFlush(firstUser);

        User savedSecond =
                userRepository.saveAndFlush(secondUser);

        assertThat(savedFirst.getUserId())
                .isNotNull();

        assertThat(savedSecond.getUserId())
                .isNotNull();

        assertThat(savedFirst.getUserId())
                .isNotEqualTo(savedSecond.getUserId());
    }

    @Test
    void saveUser_shouldPopulateAuditTimestamps() {

        User user = createUser(
                "john@example.com",
                Role.ROLE_USER);

        User savedUser =
                userRepository.saveAndFlush(user);

        assertThat(savedUser.getCreatedAt())
                .isNotNull();

        assertThat(savedUser.getUpdatedAt())
                .isNotNull();

        assertThat(savedUser.getUpdatedAt())
                .isEqualTo(savedUser.getCreatedAt());
    }

    @Test
    void saveUser_shouldEnforceUniqueEmail() {

        User firstUser = createUser(
                "duplicate@example.com",
                Role.ROLE_USER);

        User secondUser = createUser(
                "duplicate@example.com",
                Role.ROLE_ADMIN);

        userRepository.saveAndFlush(firstUser);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(secondUser));
    }

    private User createUser(
            String email,
            Role role) {

        User user = new User();

        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmail(email);
        user.setPasswordHash("hashed-password");
        user.setRole(role);

        return user;
    }
}