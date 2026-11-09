package tn.kira.louage.louageservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tn.kira.louage.louageservice.entity.Louage;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LouageServiceIntegrationTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:louage-it;DB_CLOSE_DELAY=-1");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("eureka.client.register-with-eureka", () -> "false");
        registry.add("eureka.client.fetch-registry", () -> "false");
    }

    @Autowired
    private TestRestTemplate rest;

    private static String uniquePlate() {
        return "IT-" + System.nanoTime();
    }

    private static Louage louage(String plate, int capacity, Louage.Status status) {
        return Louage.builder()
                .plateNumber(plate)
                .capacity(capacity)
                .driverName("Integration Driver")
                .status(status)
                .build();
    }

    private ResponseEntity<Louage> put(Long id, Louage body) {
        return rest.exchange("/louages/{id}", HttpMethod.PUT, new HttpEntity<>(body), Louage.class, id);
    }

    @Test
    void seededLouagesAreAvailable() {
        ResponseEntity<Louage[]> response = rest.getForEntity("/louages", Louage[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(response.getBody())
                .extracting(Louage::getPlateNumber)
                .contains("123TUN4567");
    }

    @Test
    void getOneReturnsSeededLouage() {
        ResponseEntity<Louage> response = rest.getForEntity("/louages/1", Louage.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getCapacity()).isEqualTo(9);
    }

    @Test
    void getOneReturns404ForUnknownId() {
        ResponseEntity<String> response = rest.getForEntity("/louages/999999", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createPersistsLouageAndReturns201() {
        Louage input = louage(uniquePlate(), 7, Louage.Status.AVAILABLE);

        ResponseEntity<Louage> created = rest.postForEntity("/louages", input, Louage.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().getId()).isNotNull();

        ResponseEntity<Louage> fetched = rest.getForEntity("/louages/" + created.getBody().getId(), Louage.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().getPlateNumber()).isEqualTo(input.getPlateNumber());

        rest.delete("/louages/" + created.getBody().getId());
    }

    @Test
    void createDefaultsStatusToAvailable() {
        Louage input = louage(uniquePlate(), 7, null);

        ResponseEntity<Louage> created = rest.postForEntity("/louages", input, Louage.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().getStatus()).isEqualTo(Louage.Status.AVAILABLE);

        rest.delete("/louages/" + created.getBody().getId());
    }

    @Test
    void createRejectsNonPositiveCapacityWith400() {
        ResponseEntity<String> response = rest.postForEntity(
                "/louages", louage(uniquePlate(), 0, Louage.Status.AVAILABLE), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("capacity must be positive");
    }

    @Test
    void createRejectsBlankPlateWith400() {
        ResponseEntity<String> response = rest.postForEntity(
                "/louages", louage("   ", 7, Louage.Status.AVAILABLE), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("plateNumber is required");
    }

    @Test
    void updateAppliesChanges() {
        ResponseEntity<Louage> created = rest.postForEntity(
                "/louages", louage(uniquePlate(), 7, Louage.Status.AVAILABLE), Louage.class);
        Long id = created.getBody().getId();

        ResponseEntity<Louage> updated = put(id, louage("IT-UPD-" + id, 8, Louage.Status.ON_ROAD));

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().getCapacity()).isEqualTo(8);
        assertThat(updated.getBody().getStatus()).isEqualTo(Louage.Status.ON_ROAD);

        rest.delete("/louages/" + id);
    }

    @Test
    void updateReturns404ForUnknownId() {
        ResponseEntity<Louage> response = put(999999L, louage(uniquePlate(), 7, Louage.Status.AVAILABLE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteRemovesLouage() {
        ResponseEntity<Louage> created = rest.postForEntity(
                "/louages", louage(uniquePlate(), 7, Louage.Status.AVAILABLE), Louage.class);
        Long id = created.getBody().getId();

        ResponseEntity<Void> deleted = rest.exchange("/louages/{id}", HttpMethod.DELETE, null, Void.class, id);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rest.getForEntity("/louages/" + id, String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteReturns404ForUnknownId() {
        ResponseEntity<Void> response = rest.exchange("/louages/{id}", HttpMethod.DELETE, null, Void.class, 999999L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
