# trip-service — package walkthrough

> Port **8083** · Root package `tn.kira.louage.tripservice`
> Role (intended): **orchestrator**. A trip combines a louage + two stations and manages a
> seat counter. The service should call `louage-service` and `station-service` over Feign.
>
> Role (actual today): plain CRUD that stores foreign **ids** as numbers. No Feign client,
> no `@Transient` enrichment, no fallbacks, no seat-capacity business rule. The dependencies
> and YAML are ready; the code is not written yet.

## Package layout

```
trip-service/
├── pom.xml
└── src/main/
    ├── java/tn/kira/louage/tripservice/
    │   ├── TripServiceApplication.java     ← has @EnableFeignClients
    │   ├── config/DataSeeder.java
    │   ├── controller/TripController.java
    │   ├── entity/Trip.java
    │   └── repository/TripRepository.java
    └── resources/application.yml           ← Feign + Resilience4j configuration
```

## Intended architecture vs. reality

```
INTENDED                                        ACTUAL
┌─────────────── trip-service ───────────────┐   ┌─────────────── trip-service ───────────────┐
│ TripController                             │   │ TripController                             │
│   └─ TripService (@Transient enrichment)   │   │   └─ TripRepository (CRUD only)            │
│        ├─ LouageClient  ──Feign──► :8081   │   │        └─ trips table                      │
│        └─ StationClient ──Feign──► :8082   │   │                                            │
│           + fallbacks (Resilience4j)       │   │  (no outbound calls at all)                │
└────────────────────────────────────────────┘   └────────────────────────────────────────────┘
```

| Piece | Status |
|---|---|
| `Trip` entity with `louageId`, `departureStationId`, `destinationStationId` | ✅ |
| `TripRepository` / `TripController` CRUD | ✅ |
| `@EnableFeignClients` on the application | ✅ |
| `@FeignClient` interfaces (`LouageClient`, `StationClient`) | ❌ missing |
| DTOs for louage/station responses | ❌ missing |
| `@Transient` fields on `Trip` (`louage`, `departureStation`, `destinationStation`) | ❌ missing |
| Fallback classes | ❌ missing |
| Seat counter → `DEPARTED` when full; louage → `ON_ROAD` | ❌ missing |
| Resilience4j actually used | ⚠️ configured in YAML, unreachable without Feign |

This matters when reading the YAML: every Feign/Resilience4j setting below is currently
**inert** (loaded into configuration properties, never consumed by a call).

## `pom.xml` — what is unique here

Same base dependencies as the other business services, plus:

| Lines | Dependency | Why |
|---|---|---|
| 34–37 | `spring-cloud-starter-openfeign` | Declarative HTTP clients: annotate an interface, Spring generates the implementation. |
| 38–41 | `spring-cloud-starter-circuitbreaker-resilience4j` | Circuit breaker implementation. Required for `fallback` methods on Feign clients; without it, `spring.cloud.openfeign.circuitbreaker.enabled: true` would fail at startup. |
| 21–24 | `spring-boot-starter-data-jpa` | Trip is persisted in H2 like the other services. |
| 17–20 | `spring-boot-starter-web` | Normal servlet MVC (trip-service is **not** reactive). |
| 42–46 | springdoc | Swagger UI. |
| 30–33 | Eureka client | Needed both for registration and for resolving `lb://`-style Feign targets by service name. |

## `application.yml` line by line

```yaml
 1: server:
 2:   port: 8083
 4: spring:
 5:   application:
 6:     name: trip-service
 7:   datasource:
 8:     url: jdbc:h2:file:./data/trip-db;AUTO_SERVER=TRUE
 9:     username: sa
10:     password:
11:   jpa:
12:     hibernate:
13:       ddl-auto: update
14:     show-sql: true
15:   h2:
16:     console:
17:       enabled: true
18:   cloud:
19:     openfeign:
20:       circuitbreaker:
21:         enabled: true
22:       client:
23:         config:
24:           default:
25:             connect-timeout: 2000
26:             read-timeout: 3000
28: resilience4j:
29:   circuitbreaker:
30:     configs:
31:       default:
32:         sliding-window-size: 5
33:         minimum-number-of-calls: 3
34:         failure-rate-threshold: 50
35:         wait-duration-in-open-state: 10s
36:         permitted-number-of-calls-in-half-open-state: 2
38: eureka:
39:   client:
40:     service-url:
41:       defaultZone: http://localhost:8761/eureka
```

| Lines | Explanation |
|---|---|
| 1–2 | Port 8083. |
| 4–6 | Eureka name `trip-service`; gateway routes `lb://trip-service` here. |
| 7–10 | Own H2 file DB `./data/trip-db.mv.db` (separate from louages/stations). |
| 11–14 | Hibernate schema update + SQL logging. |
| 15–17 | H2 console at `:8083/h2-console`. |
| 18–21 | `spring.cloud.openfeign.circuitbreaker.enabled=true` → every Feign call is wrapped in a Resilience4j circuit breaker. This is what allows `@FeignClient(fallback = ...)`. |
| 22–26 | Default Feign client settings for **every** client: `connect-timeout: 2000` ms to establish TCP, `read-timeout: 3000` ms to wait for the response. Without these, Feign's defaults are much longer (10 s / 60 s), so one dead service could stall a request for a minute. |
| 28–36 | Resilience4j circuit-breaker settings. Explained below. |
| 38–41 | Eureka URL. |

### Circuit breaker parameters

| Key | Value | Meaning |
|---|---|---|
| `sliding-window-size: 5` | 5 | Number of recent calls used to compute the failure rate (`COUNT_BASED` is the default window type). |
| `minimum-number-of-calls: 3` | 3 | Do not evaluate the failure rate until at least 3 calls have been recorded (avoids opening on 1 failure). |
| `failure-rate-threshold: 50` | 50 % | If ≥ 50 % of the window failed, the breaker goes **OPEN**. |
| `wait-duration-in-open-state: 10s` | 10 s | While OPEN every call fails fast (goes to the fallback) without hitting the network. After 10 s the breaker becomes **HALF_OPEN**. |
| `permitted-number-of-calls-in-half-open-state: 2` | 2 | In HALF_OPEN, 2 trial calls are allowed. Success → CLOSED (normal). Failure → OPEN again. |

State machine:

```
CLOSED ──(failure rate ≥ 50%, ≥ 3 calls)──► OPEN
  ▲                                            │
  │                                       (10 s wait)
  └──(2 trial calls succeed)── HALF_OPEN ◄─────┘
                                   │
                             (a trial fails)
                                   ▼
                                  OPEN
```

⚠️ None of this executes today: no `@FeignClient` exists, so no call is ever wrapped.
The properties are bound into `Resilience4jConfigurationProperties`/Feign config objects but
unused.

## How the intended Feign layer would look

Not present in the repo — this is the missing piece described by the TODOs:

```java
@FeignClient(name = "louage-service", fallback = LouageClientFallback.class)
public interface LouageClient {
    @GetMapping("/louages/{id}")
    LouageDto getLouage(@PathVariable Long id);
}

@FeignClient(name = "station-service", fallback = StationClientFallback.class)
public interface StationClient {
    @GetMapping("/stations/{id}")
    StationDto getStation(@PathVariable Long id);
}
```

- `name = "louage-service"` must match the Eureka registration; Feign + Spring Cloud
  LoadBalancer then resolve the instance (no URL needed).
- The DTO classes must match the JSON produced by those services (`id`, `plateNumber`, ...).
  Using DTOs instead of the entities avoids sharing database models across services.
- A fallback class implements the interface and returns a placeholder (e.g. a DTO with
  `"unavailable"` fields), so a dead dependency degrades gracefully instead of throwing.
- Enrichment fields on `Trip` would be:

```java
@Transient                          // not a DB column
private LouageDto louage;
@Transient
private StationDto departureStation;
@Transient
private StationDto destinationStation;
```

and filled in the controller/service after loading the trip.

## The missing business rule

Project README: *"Business rule: seat counter, DEPARTED when full"*. Intended behaviour:

1. POST `/trips/{id}/book` (or similar) increments `seatsTaken`.
2. If `seatsTaken >= louage.capacity` → set trip `status = DEPARTED`.
3. Optionally, set the louage's status to `ON_ROAD` via `louage-service`.

Today `seatsTaken` only changes when a client sends a new value through `PUT /trips/{id}`.
Nothing compares it with the louage's capacity, and this service cannot even read the
capacity (it has no call to `louage-service`).

## Files in this package

| File | Content |
|---|---|
| [TripServiceApplication.md](TripServiceApplication.md) | Bootstrap + `@EnableFeignClients` |
| [entity-Trip.md](entity-Trip.md) | Trip fields, ids as references, enum |
| [repository-TripRepository.md](repository-TripRepository.md) | Plain Spring Data interface |
| [controller-TripController.md](controller-TripController.md) | CRUD + create-time defaults |
| [config-DataSeeder.md](config-DataSeeder.md) | Two sample trips referencing ids 1–4 |
