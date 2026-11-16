package tn.kira.louage.tripservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tn.kira.louage.tripservice.entity.Trip;
import tn.kira.louage.tripservice.repository.TripRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TripSecurityIntegrationTest {

    private static final StubServices STUB = StubServices.start();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:trip-sec;DB_CLOSE_DELAY=-1");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("eureka.client.register-with-eureka", () -> "false");
        registry.add("eureka.client.fetch-registry", () -> "false");

        registry.add("spring.cloud.discovery.client.simple.instances.louage-service[0].instance-id", () -> "louage-1");
        registry.add("spring.cloud.discovery.client.simple.instances.louage-service[0].service-id", () -> "louage-service");
        registry.add("spring.cloud.discovery.client.simple.instances.louage-service[0].host", () -> "127.0.0.1");
        registry.add("spring.cloud.discovery.client.simple.instances.louage-service[0].port", STUB::port);
        registry.add("spring.cloud.discovery.client.simple.instances.louage-service[0].secure", () -> "false");

        registry.add("spring.cloud.discovery.client.simple.instances.station-service[0].instance-id", () -> "station-1");
        registry.add("spring.cloud.discovery.client.simple.instances.station-service[0].service-id", () -> "station-service");
        registry.add("spring.cloud.discovery.client.simple.instances.station-service[0].host", () -> "127.0.0.1");
        registry.add("spring.cloud.discovery.client.simple.instances.station-service[0].port", STUB::port);
        registry.add("spring.cloud.discovery.client.simple.instances.station-service[0].secure", () -> "false");
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private TripRepository repository;

    @AfterAll
    static void stopStub() {
        STUB.stop();
    }

    @Test
    void requestWithoutTokenReturns401Json() {
        ResponseEntity<String> response = rest.getForEntity("/trips", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getBody()).contains("Unauthorized");
    }

    @Test
    void passengerCanRead() {
        ResponseEntity<Trip[]> response = rest.exchange(
                "/trips", HttpMethod.GET, new HttpEntity<>(bearer(TestJwt.passenger())), Trip[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void passengerCannotCreate() {
        ResponseEntity<String> response = rest.exchange(
                "/trips", HttpMethod.POST,
                new HttpEntity<>(trip(1), bearer(TestJwt.passenger())), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Forbidden");
    }

    @Test
    void driverCanCreate() {
        ResponseEntity<Trip> response = rest.exchange(
                "/trips", HttpMethod.POST,
                new HttpEntity<>(trip(1), bearer(TestJwt.driver())), Trip.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isNotNull();
    }

    @Test
    void driverCannotDelete() {
        Trip saved = repository.save(trip(1));

        ResponseEntity<String> response = rest.exchange(
                "/trips/" + saved.getId(), HttpMethod.DELETE,
                new HttpEntity<>(bearer(TestJwt.driver())), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanDelete() {
        Trip saved = repository.save(trip(1));

        ResponseEntity<Void> response = rest.exchange(
                "/trips/" + saved.getId(), HttpMethod.DELETE,
                new HttpEntity<>(bearer(TestJwt.admin())), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void passengerCanJoin() {
        Trip saved = repository.save(trip(1));

        ResponseEntity<Trip> response = rest.exchange(
                "/trips/" + saved.getId() + "/join", HttpMethod.POST,
                new HttpEntity<>(bearer(TestJwt.passenger())), Trip.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getSeatsTaken()).isEqualTo(2);
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

    private static Trip trip(int seatsTaken) {
        return Trip.builder()
                .louageId(1L)
                .departureStationId(1L)
                .destinationStationId(3L)
                .seatsTaken(seatsTaken)
                .pricePerSeat(10.0)
                .status(Trip.Status.WAITING)
                .build();
    }

    private static final class StubServices {

        private final HttpServer server;

        private StubServices(HttpServer server) {
            this.server = server;
        }

        static StubServices start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                StubServices stub = new StubServices(server);
                server.createContext("/louages/", stub::handleLouage);
                server.createContext("/stations/", stub::handleStation);
                server.start();
                return stub;
            } catch (IOException ex) {
                throw new IllegalStateException("Could not start stub services", ex);
            }
        }

        int port() {
            return server.getAddress().getPort();
        }

        void stop() {
            server.stop(0);
        }

        private void handleLouage(HttpExchange exchange) throws IOException {
            String id = exchange.getRequestURI().getPath().substring("/louages/".length());
            respond(exchange, 200, "{\"id\":" + id
                    + ",\"plateNumber\":\"123TUN4567\",\"capacity\":9,"
                    + "\"driverName\":\"Ali Ben Salah\",\"status\":\"AVAILABLE\"}");
        }

        private void handleStation(HttpExchange exchange) throws IOException {
            String id = exchange.getRequestURI().getPath().substring("/stations/".length());
            respond(exchange, 200, "{\"id\":" + id
                    + ",\"name\":\"Station " + id + "\","
                    + "\"city\":\"City " + id + "\",\"governorate\":\"Governorate " + id + "\"}");
        }

        private static void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
    }
}
