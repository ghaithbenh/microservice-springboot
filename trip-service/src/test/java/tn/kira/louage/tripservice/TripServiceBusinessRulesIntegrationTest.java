package tn.kira.louage.tripservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tn.kira.louage.tripservice.client.LouageClient;
import tn.kira.louage.tripservice.client.StationClient;
import tn.kira.louage.tripservice.entity.Trip;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TripServiceBusinessRulesIntegrationTest {

    private static final StubServices STUB = StubServices.start();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:trips-it;DB_CLOSE_DELAY=-1");
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

        registry.add("resilience4j.circuitbreaker.configs.default.sliding-window-size", () -> "10");
        registry.add("resilience4j.circuitbreaker.configs.default.minimum-number-of-calls", () -> "10");
        registry.add("resilience4j.circuitbreaker.configs.default.failure-rate-threshold", () -> "50");
        registry.add("resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state", () -> "1s");
        registry.add("resilience4j.timelimiter.configs.default.timeout-duration", () -> "10s");
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private LouageClient louageClient;

    @Autowired
    private StationClient stationClient;

    @BeforeEach
    void warmUpFeign() {
        louageClient.getLouage(1L);
        stationClient.getStation(1L);
    }

    @AfterAll
    static void stopStub() {
        STUB.stop();
    }

    // ---------------------------------------------------------------- helpers

    private static Trip trip(Long louageId, Long departureStationId, Long destinationStationId,
                             int seatsTaken, double pricePerSeat) {
        return Trip.builder()
                .louageId(louageId)
                .departureStationId(departureStationId)
                .destinationStationId(destinationStationId)
                .seatsTaken(seatsTaken)
                .pricePerSeat(pricePerSeat)
                .status(Trip.Status.WAITING)
                .build();
    }

    private Long createTrip(int seatsTaken, double pricePerSeat) {
        ResponseEntity<Trip> created = rest.postForEntity(
                "/trips", trip(1L, 1L, 3L, seatsTaken, pricePerSeat), Trip.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().getId()).isNotNull();
        return created.getBody().getId();
    }

    private void deleteTrip(Long id) {
        rest.delete("/trips/" + id);
    }

    // ---------------------------------------------------------------- reads

    @Test
    void getTripsReturnsSeededTripsWithRemoteEnrichment() {
        ResponseEntity<Trip[]> response = rest.getForEntity("/trips", Trip[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(response.getBody()[0].getLouage()).isNotNull();
        assertThat(response.getBody()[0].getLouage().getPlateNumber()).isEqualTo("123TUN4567");
        assertThat(response.getBody()[0].getDepartureStation()).isNotNull();
    }

    @Test
    void getOneReturnsTripWithRemoteEnrichment() {
        ResponseEntity<Trip> response = rest.getForEntity("/trips/1", Trip.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getLouage().getCapacity()).isEqualTo(9);
        assertThat(response.getBody().getDepartureStation().getName()).isEqualTo("Station 1");
        assertThat(response.getBody().getDestinationStation().getName()).isEqualTo("Station 3");
    }

    @Test
    void getOneReturns404ForUnknownId() {
        ResponseEntity<String> response = rest.getForEntity("/trips/999999", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------------------------------------------------------------- create

    @Test
    void createPersistsTripAndReturns201() {
        Long id = createTrip(0, 12.5);

        ResponseEntity<Trip> fetched = rest.getForEntity("/trips/" + id, Trip.class);

        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().getLouage().getPlateNumber()).isEqualTo("123TUN4567");
        assertThat(fetched.getBody().getDepartureStation().getName()).isEqualTo("Station 1");

        deleteTrip(id);
    }

    @Test
    void createDefaultsStatusAndKeepsZeroSeats() {
        Trip input = trip(1L, 1L, 3L, 0, 12.5);
        input.setStatus(null);

        ResponseEntity<Trip> created = rest.postForEntity("/trips", input, Trip.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().getSeatsTaken()).isZero();
        assertThat(created.getBody().getStatus()).isEqualTo(Trip.Status.WAITING);

        deleteTrip(created.getBody().getId());
    }

    @Test
    void createRejectsMissingLouageIdWith400() {
        Trip input = trip(null, 1L, 3L, 0, 12.5);

        ResponseEntity<String> response = rest.postForEntity("/trips", input, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("louageId is required");
    }

    @Test
    void createRejectsUnknownLouageWith400() {
        Trip input = trip(9999L, 1L, 3L, 0, 12.5);

        ResponseEntity<String> response = rest.postForEntity("/trips", input, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Louage not found: 9999");
    }

    @Test
    void createRejectsUnknownStationWith400() {
        Trip input = trip(1L, 9999L, 3L, 0, 12.5);

        ResponseEntity<String> response = rest.postForEntity("/trips", input, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Departure station not found: 9999");
    }

    @Test
    void createRejectsSeatsAboveCapacityWith400() {
        Trip input = trip(1L, 1L, 3L, 10, 12.5);

        ResponseEntity<String> response = rest.postForEntity("/trips", input, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("seatsTaken cannot exceed louage capacity (9)");
    }

    @Test
    void createRejectsNonPositivePriceWith400() {
        Trip input = trip(1L, 1L, 3L, 0, -1.0);

        ResponseEntity<String> response = rest.postForEntity("/trips", input, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("pricePerSeat must be positive");
    }

    // ---------------------------------------------------------------- join

    @Test
    void joinIncrementsSeatAndReturnsUpdatedTrip() {
        Long id = createTrip(4, 12.5);

        ResponseEntity<Trip> joined = rest.postForEntity("/trips/" + id + "/join", null, Trip.class);

        assertThat(joined.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(joined.getBody().getSeatsTaken()).isEqualTo(5);
        assertThat(joined.getBody().getStatus()).isEqualTo(Trip.Status.WAITING);

        deleteTrip(id);
    }

    @Test
    void joinMarksTripDepartedAtCapacityAndThenRefuses() {
        Long id = createTrip(8, 12.5);

        ResponseEntity<Trip> joined = rest.postForEntity("/trips/" + id + "/join", null, Trip.class);

        assertThat(joined.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(joined.getBody().getSeatsTaken()).isEqualTo(9);
        assertThat(joined.getBody().getStatus()).isEqualTo(Trip.Status.DEPARTED);

        ResponseEntity<String> again = rest.postForEntity("/trips/" + id + "/join", null, String.class);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(again.getBody()).contains("already departed");

        deleteTrip(id);
    }

    @Test
    void joinReturns404ForUnknownTrip() {
        ResponseEntity<String> response = rest.postForEntity("/trips/999999/join", null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void concurrentJoinsNeverExceedCapacity() throws Exception {
        Long id = createTrip(8, 12.5);
        int attempts = 6;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);

        try {
            CountDownLatch ready = new CountDownLatch(attempts);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();

            for (int i = 0; i < attempts; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return rest.postForEntity("/trips/" + id + "/join", null, String.class)
                            .getStatusCode()
                            .value();
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(20, TimeUnit.SECONDS));
            }

            assertThat(statuses).allMatch(status -> status == 200 || status == 400);
            assertThat(statuses).filteredOn(status -> status == 200).hasSize(1);

            ResponseEntity<Trip> trip = rest.getForEntity("/trips/" + id, Trip.class);
            assertThat(trip.getBody().getSeatsTaken()).isEqualTo(9);
            assertThat(trip.getBody().getStatus()).isEqualTo(Trip.Status.DEPARTED);
        } finally {
            pool.shutdownNow();
        }

        deleteTrip(id);
    }

    // ---------------------------------------------------------------- update / delete

    @Test
    void updateAppliesChanges() {
        Long id = createTrip(2, 10.0);

        ResponseEntity<Trip> updated = rest.exchange(
                "/trips/" + id,
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(trip(1L, 1L, 3L, 6, 20.0)),
                Trip.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().getSeatsTaken()).isEqualTo(6);
        assertThat(updated.getBody().getPricePerSeat()).isEqualTo(20.0);

        deleteTrip(id);
    }

    @Test
    void updateRejectsSeatsAboveCapacityWith400() {
        Long id = createTrip(2, 10.0);

        ResponseEntity<String> response = rest.exchange(
                "/trips/" + id,
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(trip(1L, 1L, 3L, 10, 10.0)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("cannot exceed louage capacity");

        deleteTrip(id);
    }

    @Test
    void updateReturns404ForUnknownId() {
        ResponseEntity<String> response = rest.exchange(
                "/trips/999999",
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(trip(1L, 1L, 3L, 0, 10.0)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteRemovesTrip() {
        Long id = createTrip(0, 10.0);

        ResponseEntity<Void> deleted = rest.exchange(
                "/trips/" + id, org.springframework.http.HttpMethod.DELETE, null, Void.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rest.getForEntity("/trips/" + id, String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteReturns404ForUnknownId() {
        ResponseEntity<Void> response = rest.exchange(
                "/trips/999999", org.springframework.http.HttpMethod.DELETE, null, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------------------------------------------------------------- stub

    private static final class StubServices {

        private final HttpServer server;
        private final ExecutorService executor;

        private StubServices(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static StubServices start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                ExecutorService executor = Executors.newFixedThreadPool(8);
                server.setExecutor(executor);
                StubServices stub = new StubServices(server, executor);
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
            executor.shutdownNow();
        }

        private void handleLouage(HttpExchange exchange) throws IOException {
            String id = exchange.getRequestURI().getPath().substring("/louages/".length());
            if ("9999".equals(id)) {
                respondEmpty(exchange, 404);
                return;
            }
            respond(exchange, 200, "{\"id\":" + id
                    + ",\"plateNumber\":\"123TUN4567\",\"capacity\":9,"
                    + "\"driverName\":\"Ali Ben Salah\",\"status\":\"AVAILABLE\"}");
        }

        private void handleStation(HttpExchange exchange) throws IOException {
            String id = exchange.getRequestURI().getPath().substring("/stations/".length());
            if ("9999".equals(id)) {
                respondEmpty(exchange, 404);
                return;
            }
            respond(exchange, 200, "{\"id\":" + id
                    + ",\"name\":\"Station " + id + "\","
                    + "\"city\":\"City " + id + "\",\"governorate\":\"Governorate " + id + "\"}");
        }

        private static void respondEmpty(HttpExchange exchange, int status) throws IOException {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
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
