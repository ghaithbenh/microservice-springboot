package tn.kira.louage.louageservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tn.kira.louage.louageservice.entity.Louage;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LouageSecurityIntegrationTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:louage-sec;DB_CLOSE_DELAY=-1");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("eureka.client.register-with-eureka", () -> "false");
        registry.add("eureka.client.fetch-registry", () -> "false");
    }

    @Autowired
    private TestRestTemplate rest;

    @Test
    void requestWithoutTokenReturns401Json() {
        ResponseEntity<String> response = rest.getForEntity("/louages", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getBody()).contains("Unauthorized");
    }

    @Test
    void passengerCanRead() {
        ResponseEntity<Louage[]> response = rest.exchange(
                "/louages", HttpMethod.GET, new HttpEntity<>(bearer(TestJwt.passenger())), Louage[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void passengerCannotWrite() {
        ResponseEntity<String> response = rest.exchange(
                "/louages", HttpMethod.POST,
                new HttpEntity<>(louage(), bearer(TestJwt.passenger())), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Forbidden");
    }

    @Test
    void adminCanWrite() {
        ResponseEntity<Louage> created = rest.exchange(
                "/louages", HttpMethod.POST,
                new HttpEntity<>(louage(), bearer(TestJwt.admin())), Louage.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long id = created.getBody().getId();

        ResponseEntity<Void> deleted = rest.exchange(
                "/louages/" + id, HttpMethod.DELETE,
                new HttpEntity<>(bearer(TestJwt.admin())), Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void actuatorHealthAndApiDocsArePublic() {
        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/v3/api-docs", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private static Louage louage() {
        return Louage.builder()
                .plateNumber("SEC-" + System.nanoTime())
                .capacity(7)
                .driverName("Security Test")
                .status(Louage.Status.AVAILABLE)
                .build();
    }
}
