package com.microservice.user.security.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.microservice.user.entity.User;
import com.microservice.user.enums.Role;
import com.microservice.user.repository.UserRepository;
import com.microservice.user.validation.PasswordPolicy;

@Component
public class InitialAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapAdminProperties properties;

    public InitialAdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder, BootstrapAdminProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        /*
         * Bootstrap must be explicitly enabled.
         */
        if (!properties.isEnabled()) {
            log.info("Initial administrator bootstrap is disabled.");
            return;
        }

        validateConfiguration();

        /*
         * =========================================================
         * CASE 1
         * =========================================================
         *
         * An administrator already exists.
         *
         * Never automatically create another administrator.
         */
        if (userRepository.existsByRole(Role.ROLE_ADMIN)) {
            userRepository.findByEmail(properties.getEmail()).ifPresentOrElse(existingUser -> {if (existingUser.getRole() == Role.ROLE_ADMIN) {
            	log.info("Bootstrap administrator already exists: {}", properties.getEmail());
            	return;
            }

                                /*
                                 * The configured bootstrap email belongs
                                 * to a normal user while another admin
                                 * already exists.
                                 *
                                 * Do not silently promote the user.
                                 */
                                throw new IllegalStateException(
                                        "Bootstrap admin email belongs to "
                                                + "a non-admin user: "
                                                + properties.getEmail()
                                );
                            },

                            () -> {

                                /*
                                 * A different admin already exists.
                                 *
                                 * Refuse to silently create another one.
                                 */
                                throw new IllegalStateException(
                                        "An administrator already exists, "
                                                + "but not for the configured "
                                                + "bootstrap admin email: "
                                                + properties.getEmail()
                                );
                            }
                    );

            return;
        }

        /*
         * =========================================================
         * CASE 2
         * =========================================================
         *
         * No administrator exists.
         */
        if (userRepository.existsByEmail(properties.getEmail())) {

            /*
             * Never automatically promote an existing normal user.
             */
            throw new IllegalStateException(
                    "Bootstrap admin email already belongs to an "
                            + "existing non-admin user: "
                            + properties.getEmail()
            );
        }

        /*
         * Password is encoded before persistence.
         */
        String encodedPassword = passwordEncoder.encode(properties.getPassword());

        /*
         * Explicitly create ROLE_ADMIN.
         */
        User admin = User.createAdmin(properties.getFirstName(), properties.getLastName(), properties.getEmail(), encodedPassword);
        userRepository.save(admin);

        log.info("Initial administrator created successfully: {}", properties.getEmail());
    }

    private void validateConfiguration() {
        if (isBlank(properties.getEmail())) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_EMAIL must be configured " + "when initial admin bootstrap is enabled");
        }

        if (isBlank(properties.getPassword())) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must be configured " + "when initial admin bootstrap is enabled");
        }

        if (isBlank(properties.getFirstName())) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_FIRST_NAME must be configured " + "when initial admin bootstrap is enabled");
        }

        if (isBlank(properties.getLastName())) {

            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_LAST_NAME must be configured "
                            + "when initial admin bootstrap is enabled"
            );
        }

        if (!properties.getPassword()
                .matches(PasswordPolicy.REGEX)) {

            throw new IllegalStateException(
                    "Bootstrap administrator password does not satisfy "
                            + "the application password policy"
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}