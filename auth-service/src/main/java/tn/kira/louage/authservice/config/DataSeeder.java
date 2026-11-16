package tn.kira.louage.authservice.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import lombok.extern.slf4j.Slf4j;
import tn.kira.louage.authservice.entity.AppUser;
import tn.kira.louage.authservice.entity.Role;
import tn.kira.louage.authservice.repository.AppUserRepository;

@Configuration
@Slf4j
public class DataSeeder {

    @Bean
    CommandLineRunner seedUsers(AppUserRepository repository, PasswordEncoder passwordEncoder) {
        return args -> {
            seed(repository, passwordEncoder, "admin", "admin123", Role.ADMIN);
            seed(repository, passwordEncoder, "driver", "driver123", Role.DRIVER);
            seed(repository, passwordEncoder, "passenger", "passenger123", Role.PASSENGER);
            log.info("Seeded default users: admin, driver, passenger.");
        };
    }

    private void seed(AppUserRepository repository, PasswordEncoder passwordEncoder,
            String username, String rawPassword, Role role) {
        if (repository.existsByUsername(username)) {
            return;
        }
        repository.save(AppUser.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .build());
    }
}
