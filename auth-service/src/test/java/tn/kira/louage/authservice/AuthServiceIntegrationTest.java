package tn.kira.louage.authservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tn.kira.louage.authservice.dto.AuthResponse;
import tn.kira.louage.authservice.dto.LoginRequest;
import tn.kira.louage.authservice.dto.RegisterRequest;
import tn.kira.louage.authservice.entity.Role;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthServiceIntegrationTest {

    private static final String SECRET = "louage-tracker-dev-secret-key-32-chars-min";
    private static final SecretKey KEY =
            new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:auth-it;DB_CLOSE_DELAY=-1");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("eureka.client.register-with-eureka", () -> "false");
        registry.add("eureka.client.fetch-registry", () -> "false");
    }

    @Autowired
    private TestRestTemplate rest;

    @Test
    void seededAdminCanLoginAndReceivesJwt() {
        ResponseEntity<AuthResponse> response = rest.postForEntity(
                "/auth/login", new LoginRequest("admin", "admin123"), AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isNotBlank();
        assertThat(response.getBody().tokenType()).isEqualTo("Bearer");
        assertThat(response.getBody().role()).isEqualTo("ADMIN");
        assertThat(response.getBody().expiresIn()).isEqualTo(3600);

        Jwt jwt = NimbusJwtDecoder.withSecretKey(KEY).build().decode(response.getBody().token());
        assertThat(jwt.getSubject()).isEqualTo("admin");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now().plusSeconds(3500));
    }

    @Test
    void loginWithWrongPasswordReturns401() {
        ResponseEntity<String> response = rest.postForEntity(
                "/auth/login", new LoginRequest("admin", "wrong-password"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Invalid username or password");
    }

    @Test
    void registerCreatesDriverAndLoginWorks() {
        String username = "it-driver-" + System.nanoTime();

        ResponseEntity<String> created = rest.postForEntity(
                "/auth/register", new RegisterRequest(username, "secret123", Role.DRIVER), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<AuthResponse> login = rest.postForEntity(
                "/auth/login", new LoginRequest(username, "secret123"), AuthResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody().role()).isEqualTo("DRIVER");
    }

    @Test
    void registerDuplicateUsernameReturns409() {
        ResponseEntity<String> response = rest.postForEntity(
                "/auth/register", new RegisterRequest("admin", "secret123", Role.PASSENGER), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("username already exists");
    }

    @Test
    void registerAdminRoleIsRejected() {
        ResponseEntity<String> response = rest.postForEntity(
                "/auth/register",
                new RegisterRequest("it-admin-" + System.nanoTime(), "secret123", Role.ADMIN),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("role must be DRIVER or PASSENGER");
    }

    @Test
    void registerRejectsBlankUsername() {
        ResponseEntity<String> response = rest.postForEntity(
                "/auth/register", new RegisterRequest("   ", "secret123", Role.PASSENGER), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("username is required");
    }

    @Test
    void registerRejectsShortPassword() {
        ResponseEntity<String> response = rest.postForEntity(
                "/auth/register",
                new RegisterRequest("it-short-" + System.nanoTime(), "123", Role.PASSENGER),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("password must be at least 6 characters");
    }
}
