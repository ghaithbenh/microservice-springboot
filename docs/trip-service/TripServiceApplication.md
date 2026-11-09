# TripServiceApplication

> File: `trip-service/src/main/java/tn/kira/louage/tripservice/TripServiceApplication.java`
> Package: `tn.kira.louage.tripservice`
> Type: Spring Boot bootstrap + Feign enablement

## The code

```java
 1: package tn.kira.louage.tripservice;
 2:
 3: import org.springframework.boot.SpringApplication;
 4: import org.springframework.boot.autoconfigure.SpringBootApplication;
 5: import org.springframework.cloud.openfeign.EnableFeignClients;
 6:
 7: @SpringBootApplication
 8: @EnableFeignClients
 9: public class TripServiceApplication {
10:     public static void main(String[] args) {
11:         SpringApplication.run(TripServiceApplication.class, args);
12:     }
13: }
14: // TODO: entity Trip (+ @Transient louage, departureStation, destinationStation),
15: //       TripRepository, TripController, Feign clients (louage-service, station-service),
16: //       fallback classes, DTOs Louage and Station
```

## Line by line

| Lines | Explanation |
|---|---|
| 1 | Root package, scanned recursively. |
| 3–4 | Standard Boot imports. |
| 5 | `@EnableFeignClients` import. |
| 7 | `@SpringBootApplication` — MVC, JPA, H2, Eureka, springdoc auto-config. |
| 8 | `@EnableFeignClients` — tells Spring Cloud to scan this package (and sub-packages) for interfaces annotated `@FeignClient`, and to register an implementation bean for each. 🔍 **There are zero such interfaces today**, so this annotation currently has no observable effect. The moment you add `LouageClient`, it is picked up automatically. |
| 9 | Class declaration. |
| 10 | JVM entry point. |
| 11 | Starts the context: creates the `TripController`, `TripRepository` proxy, `DataSeeder`, connects to `trip-db`, starts Tomcat on 8083, registers with Eureka, runs the seeder. |
| 12–13 | Closing braces. |
| 14–16 | **Accurate TODO list** — entity, repository and controller exist; the Feign clients, fallbacks, DTOs and `@Transient` fields do not. Keep this comment; it is the roadmap. |

## What `@EnableFeignClients` will do once clients exist

1. Scan for interfaces with `@FeignClient`.
2. For each, build a `FeignClientFactoryBean` that creates a **dynamic proxy** implementing
   the interface.
3. The proxy turns each method call into an HTTP request:
   - method + `@GetMapping("/louages/{id}")` → `GET http://<resolved-instance>/louages/{id}`;
   - service name → looked up in Eureka through Spring Cloud LoadBalancer;
   - return type → deserialized from JSON.
4. Because `spring.cloud.openfeign.circuitbreaker.enabled=true`, the call is wrapped in a
   Resilience4j circuit breaker; failures can route to the `fallback` class.
5. Timeouts come from `spring.cloud.openfeign.client.config.default`.

Example of the target setup (not in the repo):

```java
@FeignClient(name = "louage-service", fallback = LouageClientFallback.class)
public interface LouageClient {
    @GetMapping("/louages/{id}")
    LouageDto getLouage(@PathVariable Long id);
}
```

## Startup sequence

1. Load YAML: port 8083, H2 `trip-db`, Feign timeouts, Resilience4j thresholds.
2. Create the datasource + JPA; Hibernate creates the `trips` table.
3. Component scan → `TripController`, `TripRepository`, `DataSeeder`.
4. `@EnableFeignClients` scan → finds nothing today (no error).
5. Tomcat on 8083, Eureka registration.
6. `CommandLineRunner` seeds 2 trips.
7. Ready.

## How to run

```bat
mvn -pl trip-service spring-boot:run
:: or
java -jar trip-service/target/trip-service-1.0.0.jar
```

- API: http://localhost:8083/trips
- Swagger: http://localhost:8083/swagger-ui.html
- H2 console: http://localhost:8083/h2-console

⚠️ If you ever enable `spring.cloud.openfeign.circuitbreaker.enabled=true` **without** the
`spring-cloud-starter-circuitbreaker-resilience4j` dependency, startup fails with
"No Feign CircuitBreaker implementation found". The dependency is present in the POM
(lines 38–41), so this module is safe.

## Note on `@EnableFeignClients` placement

It lives on the application class, which is the scan root, so any future client interface
inside `tn.kira.louage.tripservice.**` is discovered. You could also use
`@EnableFeignClients(basePackages = "tn.kira.louage.tripservice.client")` to be explicit.
