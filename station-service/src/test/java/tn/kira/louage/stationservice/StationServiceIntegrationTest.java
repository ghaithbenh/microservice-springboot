package tn.kira.louage.stationservice;

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

import tn.kira.louage.stationservice.entity.Station;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StationServiceIntegrationTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:station-it;DB_CLOSE_DELAY=-1");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("eureka.client.register-with-eureka", () -> "false");
        registry.add("eureka.client.fetch-registry", () -> "false");
    }

    @Autowired
    private TestRestTemplate rest;

    private static Station station(String name) {
        return Station.builder()
                .name(name)
                .city("Integration City")
                .governorate("Integration Governorate")
                .build();
    }

    private ResponseEntity<Station> put(Long id, Station body) {
        return rest.exchange("/stations/{id}", HttpMethod.PUT, new HttpEntity<>(body), Station.class, id);
    }

    @Test
    void seededStationsAreAvailable() {
        ResponseEntity<Station[]> response = rest.getForEntity("/stations", Station[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSizeGreaterThanOrEqualTo(4);
        assertThat(response.getBody())
                .extracting(Station::getName)
                .contains("Station Moncef Bey");
    }

    @Test
    void getOneReturnsSeededStation() {
        ResponseEntity<Station> response = rest.getForEntity("/stations/1", Station.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getCity()).isEqualTo("Tunis");
    }

    @Test
    void getOneReturns404ForUnknownId() {
        ResponseEntity<String> response = rest.getForEntity("/stations/999999", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createPersistsStationAndReturns201() {
        Station input = station("IT Station " + System.nanoTime());

        ResponseEntity<Station> created = rest.postForEntity("/stations", input, Station.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().getId()).isNotNull();

        ResponseEntity<Station> fetched = rest.getForEntity("/stations/" + created.getBody().getId(), Station.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().getName()).isEqualTo(input.getName());

        rest.delete("/stations/" + created.getBody().getId());
    }

    @Test
    void createRejectsBlankNameWith400() {
        Station input = station(" ");

        ResponseEntity<String> response = rest.postForEntity("/stations", input, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("name is required");
    }

    @Test
    void createRejectsBlankGovernorateWith400() {
        Station input = station("IT Station " + System.nanoTime());
        input.setGovernorate("");

        ResponseEntity<String> response = rest.postForEntity("/stations", input, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("governorate is required");
    }

    @Test
    void updateAppliesChanges() {
        ResponseEntity<Station> created = rest.postForEntity(
                "/stations", station("IT Station " + System.nanoTime()), Station.class);
        Long id = created.getBody().getId();

        Station update = Station.builder()
                .name("IT Updated " + id)
                .city("New City")
                .governorate("New Governorate")
                .build();

        ResponseEntity<Station> updated = put(id, update);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().getName()).isEqualTo(update.getName());
        assertThat(updated.getBody().getCity()).isEqualTo("New City");

        rest.delete("/stations/" + id);
    }

    @Test
    void updateReturns404ForUnknownId() {
        ResponseEntity<Station> response = put(999999L, station("IT Station"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteRemovesStation() {
        ResponseEntity<Station> created = rest.postForEntity(
                "/stations", station("IT Station " + System.nanoTime()), Station.class);
        Long id = created.getBody().getId();

        ResponseEntity<Void> deleted = rest.exchange("/stations/{id}", HttpMethod.DELETE, null, Void.class, id);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rest.getForEntity("/stations/" + id, String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteReturns404ForUnknownId() {
        ResponseEntity<Void> response = rest.exchange("/stations/{id}", HttpMethod.DELETE, null, Void.class, 999999L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
