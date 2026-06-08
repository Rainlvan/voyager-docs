package com.voyager.docs.config;

import com.voyager.docs.domain.AppUser;
import com.voyager.docs.domain.UserRole;
import com.voyager.docs.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

@Configuration
public class BootstrapConfig {
    private static final Logger log = LoggerFactory.getLogger(BootstrapConfig.class);

    @Bean
    CommandLineRunner bootstrapAdmin(AppUserRepository users, PasswordEncoder encoder, Environment environment) {
        return args -> {
            createUserIfMissing(
                    users,
                    encoder,
                    value(environment, "VOYAGER_BOOTSTRAP_ADMIN_USERNAME", "admin"),
                    value(environment, "VOYAGER_BOOTSTRAP_ADMIN_DISPLAY_NAME", "System Administrator"),
                    value(environment, "VOYAGER_BOOTSTRAP_ADMIN_PASSWORD", "12345678"),
                    UserRole.ADMIN);
            createUserIfMissing(
                    users,
                    encoder,
                    value(environment, "VOYAGER_BOOTSTRAP_EMPLOYEE_USERNAME", "employee"),
                    value(environment, "VOYAGER_BOOTSTRAP_EMPLOYEE_DISPLAY_NAME", "Employee"),
                    value(environment, "VOYAGER_BOOTSTRAP_EMPLOYEE_PASSWORD", "12345678"),
                    UserRole.USER);
        };
    }

    private void createUserIfMissing(
            AppUserRepository users,
            PasswordEncoder encoder,
            String username,
            String displayName,
            String password,
            UserRole role) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.info("Skipped default {} user because username or password is blank", role.name().toLowerCase());
            return;
        }
        if (users.existsByUsername(username)) {
            return;
        }
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setDisplayName(StringUtils.hasText(displayName) ? displayName : username);
        user.setPasswordHash(encoder.encode(password));
        user.setRole(role);
        user.setEnabled(true);
        users.save(user);
        log.info("Created default {} user: {}", role.name().toLowerCase(), username);
    }

    private String value(Environment environment, String key, String fallback) {
        String value = environment.getProperty(key);
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
