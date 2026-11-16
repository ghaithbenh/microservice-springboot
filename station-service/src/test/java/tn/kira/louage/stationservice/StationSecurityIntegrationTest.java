package tn.kira.louage.stationservice;

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

import tn.kira.louage.stationservice.entity.Station;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StationSecurityIntegrationTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:station-sec;DB_CLOSE_DELAY=-1");
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
        ResponseEntity<String> response = rest.getForEntity("/stations", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getBody()).contains("Unauthorized");
    }

    @Test
    void passengerCanRead() {
        ResponseEntity<Station[]> response = rest.exchange(
                "/stations", HttpMethod.GET, new HttpEntity<>(bearer(TestJwt.passenger())), Station[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void passengerCannotWrite() {
        ResponseEntity<String> response = rest.exchange(
                "/stations", HttpMethod.POST,
                new HttpEntity<>(station(), bearer(TestJwt.passenger())), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Forbidden");
    }

    @Test
    void adminCanWrite() {
        ResponseEntity<Station> created = rest.exchange(
                "/stations", HttpMethod.POST,
                new HttpEntity<>(station(), bearer(TestJwt.admin())), Station.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long id = created.getBody().getId();

        ResponseEntity<Void> deleted = rest.exchange(
                "/stations/" + id, HttpMethod.DELETE,
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

    private static Station station() {
        return Station.builder()
                .name("SEC Station " + System.nanoTime())
                .city("Security City")
                .governorate("Security Governorate")
                .build();
    }
}
