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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tn.kira.louage.tripservice.entity.Trip;
import tn.kira.louage.tripservice.repository.TripRepository;

/**
 * Exercises the optimistic-locking join path. The louage stub reports a
 * capacity of 8, so 20 simultaneous joins must yield exactly 8 successes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.security.enabled=false")
@Import(TestSecurityConfig.class)
class TripJoinConcurrencyIntegrationTest {

    private static final int CAPACITY = 8;
    private static final StubServices STUB = StubServices.start();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:trips-concurrency;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
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

        registry.add("resilience4j.circuitbreaker.configs.default.sliding-window-size", () -> "100");
        registry.add("resilience4j.circuitbreaker.configs.default.minimum-number-of-calls", () -> "20");
        registry.add("resilience4j.circuitbreaker.configs.default.failure-rate-threshold", () -> "50");
        registry.add("resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state", () -> "1s");
        registry.add("resilience4j.timelimiter.configs.default.timeout-duration", () -> "10s");
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private TripRepository repository;

    @AfterAll
    static void stopStub() {
        STUB.stop();
    }

    private Trip newTrip(int seatsTaken) {
        return Trip.builder()
                .louageId(1L)
                .departureStationId(1L)
                .destinationStationId(3L)
                .seatsTaken(seatsTaken)
                .pricePerSeat(12.5)
                .status(Trip.Status.WAITING)
                .build();
    }

    private Long createTrip(int seatsTaken) {
        ResponseEntity<Trip> created = rest.postForEntity("/trips", newTrip(seatsTaken), Trip.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return created.getBody().getId();
    }

    @Test
    void twentySimultaneousJoinsFillExactlyTheCapacity() throws Exception {
        Long id = createTrip(0);
        int attempts = 20;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);

        List<Integer> statuses = new ArrayList<>();
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

            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(statuses).allMatch(status -> status == 200 || status == 400 || status == 409);
            assertThat(statuses).noneMatch(status -> status >= 500);
            assertThat(statuses.stream().filter(status -> status == 200).count())
                    .as("exactly the capacity may succeed")
                    .isEqualTo(CAPACITY);

            Trip trip = repository.findById(id).orElseThrow();
            assertThat(trip.getSeatsTaken()).isEqualTo(CAPACITY);
            assertThat(trip.getStatus()).isEqualTo(Trip.Status.DEPARTED);
        } finally {
            pool.shutdownNow();
            rest.delete("/trips/" + id);
        }
    }

    @Test
    void versionIncrementsOnEachJoin() {
        Long id = createTrip(0);
        try {
            Trip initial = repository.findById(id).orElseThrow();
            long version = initial.getVersion();

            for (int i = 1; i <= 3; i++) {
                ResponseEntity<Trip> joined = rest.postForEntity("/trips/" + id + "/join", null, Trip.class);
                assertThat(joined.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(joined.getBody().getVersion())
                        .as("version after join %s", i)
                        .isEqualTo(version + 1);
                version = joined.getBody().getVersion();
            }

            Trip stored = repository.findById(id).orElseThrow();
            assertThat(stored.getSeatsTaken()).isEqualTo(3);
            assertThat(stored.getVersion()).isEqualTo(3);
        } finally {
            rest.delete("/trips/" + id);
        }
    }

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
                ExecutorService executor = Executors.newFixedThreadPool(16);
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
            respond(exchange, 200, "{\"id\":" + id
                    + ",\"plateNumber\":\"123TUN4567\",\"capacity\":" + CAPACITY + ","
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
