# DataSeeder (trip-service)

> File: `trip-service/src/main/java/tn/kira/louage/tripservice/config/DataSeeder.java`
> Package: `tn.kira.louage.tripservice.config`
> Type: `@Configuration` + `@Bean CommandLineRunner`

## Purpose

Inserts two sample trips on the first start, referencing louage ids 1–2 and station ids 1–4 —
which match the rows created by the other two services' seeders on a fresh environment.

## The code

```java
 1: package tn.kira.louage.tripservice.config;
 2:
 3: import java.util.List;
 4:
 5: import org.springframework.boot.CommandLineRunner;
 6: import org.springframework.context.annotation.Bean;
 7: import org.springframework.context.annotation.Configuration;
 8:
 9: import lombok.extern.slf4j.Slf4j;
10: import tn.kira.louage.tripservice.entity.Trip;
11: import tn.kira.louage.tripservice.repository.TripRepository;
12:
13: @Configuration
14: @Slf4j
15: public class DataSeeder {
16:
17:     @Bean
18:     CommandLineRunner seedTrips(TripRepository repository) {
19:         return args -> {
20:             if (repository.count() > 0) {
21:                 log.info("Trips already seeded, skipping.");
22:                 return;
23:             }
24:             repository.saveAll(List.of(
25:                     Trip.builder()
26:                             .louageId(1L)
27:                             .departureStationId(1L)   // Tunis
28:                             .destinationStationId(3L) // Sfax
29:                             .seatsTaken(4)
30:                             .pricePerSeat(12.5)
31:                             .status(Trip.Status.WAITING)
32:                             .build(),
33:                     Trip.builder()
34:                             .louageId(2L)
35:                             .departureStationId(2L)   // Sousse
36:                             .destinationStationId(4L) // Gabès
37:                             .seatsTaken(2)
38:                             .pricePerSeat(9.0)
39:                             .status(Trip.Status.WAITING)
40:                             .build()
41:             ));
42:             log.info("Seeded 2 trips.");
43:         };
44:     }
45: }
```

## Line by line

| Lines | Explanation |
|---|---|
| 1 | Config package. |
| 3 | `List`. |
| 5–7 | `CommandLineRunner`, `@Bean`, `@Configuration`. |
| 9 | Lombok logger. |
| 10–11 | `Trip` + `TripRepository`. |
| 13–15 | Configuration class with logger. |
| 17 | `@Bean` — return value registered as a bean named `seedTrips`. |
| 18 | `TripRepository` injected as a method parameter. |
| 19 | Lambda implementing `run(String... args)`. |
| 20 | Empty-table guard (H2 file DB persists across restarts). |
| 21–22 | Already seeded → log and exit. |
| 24 | `saveAll` — two INSERTs. Unlike the louage seeder, this one has its receiver and compiles. |
| 25–32 | **Trip 1**: louage 1, depart station 1 (Tunis), arrive station 3 (Sfax), 4 seats taken, 12.5 per seat, `WAITING`. |
| 26 | `.louageId(1L)` — `L` suffix makes it a `long` literal for the `Long` field. |
| 27–28 | Station ids with **comments** that document the intended mapping to the station seeder: 1 = Tunis, 2 = Sousse, 3 = Sfax, 4 = Gabès. |
| 29 | `seatsTaken(4)` — a human-readable way to say "4 of the 9 seats sold". |
| 30 | `pricePerSeat(12.5)` — matches `double`. |
| 31 | `status(WAITING)`. |
| 32 | `build()`. |
| 33–40 | **Trip 2**: louage 2, Sousse → Gabès, 2 seats, 9.0, `WAITING`. |
| 41 | Closes `saveAll`. |
| 42 | Confirmation log. |
| 43–45 | End lambda/method/class. |

## Key teaching point: why the comments matter

`trip-service` has **no access** to the stations table, so it cannot resolve names. The
comments are documentation of a convention shared with `station-service`'s seeder. Nothing
enforces it:

- If `station-service`'s database already contained a station, the seeded ids would shift and
  the comments would be wrong.
- If a station is deleted through the API, trip 1 keeps pointing at id 3.
- If you run the three services against fresh databases, the mapping holds.

This is exactly the coupling that a Feign call + DTO (`StationDto`) would remove: instead of
relying on numeric conventions, `trip-service` could fetch and display the real station.

## Idempotency

| Situation | Result |
|---|---|
| First start | 2 trips inserted (ids 1, 2) |
| Restart | "Trips already seeded, skipping." |
| `data/` folder deleted | re-seeded |
| One row present | guard skips → stays at 1 |

## Suggested improvement

Seed only if the **specific** trips are absent, e.g. by checking
`repository.countByLouageIdAndStatus(1L, WAITING) == 0`, or - better - verify referenced
entities exist via Feign before inserting:

```java
// pseudo-code; requires the Feign clients that do not exist yet
louageClient.getLouage(1L);          // throws / falls back if missing
stationClient.getStation(1L);
```

## Startup order caveat

`trip-service` does **not** wait for `louage-service` or `station-service`; it only needs
Eureka to register itself. Seeding works regardless because there are no cross-service calls
and no foreign keys. The "trip points at louage 1" relationship is a convention, not a
constraint.
