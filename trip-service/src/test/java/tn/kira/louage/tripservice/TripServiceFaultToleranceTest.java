package tn.kira.louage.tripservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

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

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import tn.kira.louage.tripservice.client.LouageClient;
import tn.kira.louage.tripservice.client.StationClient;
import tn.kira.louage.tripservice.dto.Louage;
import tn.kira.louage.tripservice.dto.Station;
import tn.kira.louage.tripservice.entity.Trip;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TripServiceFaultToleranceTest {

    private static final StubServices STUB = StubServices.start();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:trips;DB_CLOSE_DELAY=-1");
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

        registry.add("resilience4j.circuitbreaker.configs.default.sliding-window-size", () -> "2");
        registry.add("resilience4j.circuitbreaker.configs.default.minimum-number-of-calls", () -> "2");
        registry.add("resilience4j.circuitbreaker.configs.default.failure-rate-threshold", () -> "50");
        registry.add("resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state", () -> "1s");
        registry.add("resilience4j.circuitbreaker.configs.default.permitted-number-of-calls-in-half-open-state", () -> "1");
        registry.add("resilience4j.timelimiter.configs.default.timeout-duration", () -> "6s");
    }

    @Autowired
    private LouageClient louageClient;

    @Autowired
    private StationClient stationClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void resetState() {
        STUB.reset();
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        // Warm up Feign/load balancer so the first timed call does not race client initialization.
        louageClient.getLouage(1L);
        stationClient.getStation(1L);
    }

    @AfterAll
    static void stopStub() {
        STUB.stop();
    }

    @Test
    void normalFeignCommunication() {
        Louage louage = louageClient.getLouage(1L);
        assertThat(louage.getId()).isEqualTo(1L);
        assertThat(louage.getPlateNumber()).isEqualTo("123TUN4567");
        assertThat(louage.getCapacity()).isEqualTo(9);

        Station station = stationClient.getStation(1L);
        assertThat(station.getName()).isEqualTo("Tunis");
        assertThat(station.getCity()).isEqualTo("Tunis");
    }

    @Test
    void louageServiceUnavailableUsesFallback() {
        STUB.louageAvailable = false;

        Louage louage = louageClient.getLouage(1L);

        assertThat(louage.getId()).isEqualTo(1L);
        assertThat(louage.getPlateNumber()).isEqualTo("Unavailable");
        assertThat(louage.getCapacity()).isZero();
        assertThat(louage.getDriverName()).isEqualTo("Info temporarily unavailable");
        assertThat(louage.getStatus()).isEqualTo("UNKNOWN");
    }

    @Test
    void stationServiceUnavailableUsesFallback() {
        STUB.stationAvailable = false;

        Station station = stationClient.getStation(1L);

        assertThat(station.getId()).isEqualTo(1L);
        assertThat(station.getName()).isEqualTo("Unavailable");
        assertThat(station.getCity()).isEqualTo("Unavailable");
        assertThat(station.getGovernorate()).isEqualTo("Unavailable");
    }

    @Test
    void getTripStillReturnsWhenLouageServiceUnavailable() {
        STUB.louageAvailable = false;

        ResponseEntity<Trip> response = restTemplate.getForEntity("/trips/1", Trip.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getLouage().getStatus()).isEqualTo("UNKNOWN");
        assertThat(response.getBody().getDepartureStation().getName()).isEqualTo("Tunis");
        assertThat(response.getBody().getDestinationStation().getName()).isEqualTo("Tunis");
    }

    @Test
    void getTripStillReturnsWhenStationServiceUnavailable() {
        STUB.stationAvailable = false;

        ResponseEntity<Trip> response = restTemplate.getForEntity("/trips/1", Trip.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getLouage().getPlateNumber()).isEqualTo("123TUN4567");
        assertThat(response.getBody().getDepartureStation().getName()).isEqualTo("Unavailable");
        assertThat(response.getBody().getDestinationStation().getName()).isEqualTo("Unavailable");
    }

    @Test
    void getTripsListStillReturnsWhenBothServicesUnavailable() {
        STUB.louageAvailable = false;
        STUB.stationAvailable = false;

        ResponseEntity<Trip[]> response = restTemplate.getForEntity("/trips", Trip[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody()[0].getLouage().getStatus()).isEqualTo("UNKNOWN");
        assertThat(response.getBody()[0].getDepartureStation().getName()).isEqualTo("Unavailable");
    }

    @Test
    void joinRefusesWhenLouageCapacityCannotBeVerified() {
        STUB.louageAvailable = false;

        ResponseEntity<String> response = restTemplate.postForEntity("/trips/1/join", null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void circuitBreakerOpensAndRecovers() throws InterruptedException {
        CircuitBreaker circuitBreaker = louageCircuitBreaker();
        circuitBreaker.reset();

        STUB.louageAvailable = false;
        assertThat(louageClient.getLouage(1L).getStatus()).isEqualTo("UNKNOWN");
        assertThat(louageClient.getLouage(1L).getStatus()).isEqualTo("UNKNOWN");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        STUB.louageAvailable = true;
        Thread.sleep(1200);

        Louage recovered = louageClient.getLouage(1L);

        assertThat(recovered.getPlateNumber()).isEqualTo("123TUN4567");
        assertThat(recovered.getCapacity()).isEqualTo(9);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void clientErrorsAreNotHiddenByFallback() {
        STUB.louageBadRequest = true;

        assertThatThrownBy(() -> louageClient.getLouage(1L))
                .isInstanceOf(FeignException.class)
                .satisfies(error -> assertThat(((FeignException) error).status()).isEqualTo(400));
    }

    private CircuitBreaker louageCircuitBreaker() {
        return circuitBreakerRegistry.getAllCircuitBreakers().stream()
                .filter(circuitBreaker -> circuitBreaker.getName().contains("LouageClient"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("LouageClient circuit breaker was not registered"));
    }

    private static final class StubServices {

        private final HttpServer server;
        private volatile boolean louageAvailable = true;
        private volatile boolean stationAvailable = true;
        private volatile boolean louageBadRequest = false;

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

        void reset() {
            louageAvailable = true;
            stationAvailable = true;
            louageBadRequest = false;
        }

        private void handleLouage(HttpExchange exchange) throws IOException {
            if (!louageAvailable) {
                respond(exchange, 503, "{\"error\":\"louage-service unavailable\"}");
                return;
            }
            if (louageBadRequest) {
                respond(exchange, 400, "{\"error\":\"bad request\"}");
                return;
            }
            String id = exchange.getRequestURI().getPath().substring("/louages/".length());
            respond(exchange, 200, "{\"id\":" + id
                    + ",\"plateNumber\":\"123TUN4567\",\"capacity\":9,"
                    + "\"driverName\":\"Ali Ben Salah\",\"status\":\"AVAILABLE\"}");
        }

        private void handleStation(HttpExchange exchange) throws IOException {
            if (!stationAvailable) {
                respond(exchange, 503, "{\"error\":\"station-service unavailable\"}");
                return;
            }
            String id = exchange.getRequestURI().getPath().substring("/stations/".length());
            respond(exchange, 200, "{\"id\":" + id
                    + ",\"name\":\"Tunis\",\"city\":\"Tunis\",\"governorate\":\"Tunis\"}");
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
