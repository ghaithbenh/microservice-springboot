    # DataSeeder (station-service)

> File: `station-service/src/main/java/tn/kira/louage/stationservice/config/DataSeeder.java`
> Package: `tn.kira.louage.stationservice.config`
> Type: `@Configuration` class with one `@Bean CommandLineRunner`

## Purpose

Inserts four sample stations the first time the application runs against an empty database.
It makes the API immediately useful for testing and for the `trip-service` seed data
(which references station ids 1–4).

## The code

```java
 1: package tn.kira.louage.stationservice.config;
 2:
 3: import java.util.List;
 4:
 5: import org.springframework.boot.CommandLineRunner;
 6: import org.springframework.context.annotation.Bean;
 7: import org.springframework.context.annotation.Configuration;
 8:
 9: import lombok.extern.slf4j.Slf4j;
10: import tn.kira.louage.stationservice.entity.Station;
11: import tn.kira.louage.stationservice.repository.StationRepository;
12:
13: @Configuration
14: @Slf4j
15: public class DataSeeder {
16:
17:     @Bean
18:     CommandLineRunner seedStations(StationRepository repository) {
19:         return args -> {
20:             if (repository.count() > 0) {
21:                 log.info("Stations already seeded, skipping.");
22:                 return;
23:             }
24:             repository.saveAll(List.of(
25:                     Station.builder().name("Station Moncef Bey").city("Tunis").governorate("Tunis").build(),
26:                     Station.builder().name("Station Sousse").city("Sousse").governorate("Sousse").build(),
27:                     Station.builder().name("Station Melki").city("Sfax").governorate("Sfax").build(),
28:                     Station.builder().name("Station Gabès").city("Gabès").governorate("Gabès").build()
29:             ));
30:             log.info("Seeded 4 stations: Tunis, Sousse, Sfax, Gabès.");
31:         };
32:     }
33: }
```

## Line by line

| Lines | Explanation |
|---|---|
| 1 | Config package — non-web infrastructure. |
| 3 | `List` for `List.of(...)`. |
| 5 | `CommandLineRunner` — a functional interface (`run(String... args)`). Spring Boot calls every such bean once after the context is ready. |
| 6 | `@Bean` — declares that the method's return value is a Spring bean. |
| 7 | `@Configuration` — marks the class as a bean-definition source; required for `@Bean` methods / CGLIB proxying. |
| 9 | Lombok `@Slf4j` → a `log` field. |
| 10–11 | Entity and repository used for seeding. |
| 13 | `@Configuration`: the class itself is also a bean, but its real job is to expose `seedStations()`. |
| 14 | `@Slf4j`: generates `private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);`. |
| 15 | Class declaration. |
| 17 | `@Bean` on the method below. The **method name** becomes the bean name (`seedStations`). If two beans of the same type exist, this name disambiguates them with `@Qualifier("seedStations")`. |
| 18 | The method takes a parameter `StationRepository` — Spring resolves it from the context (constructor/method-parameter injection). 🔍 Seeding is therefore inside the Spring lifecycle and can use the repository proxy. |
| 19 | Returns a lambda implementing `CommandLineRunner.run(String... args)`. The lambda body is what executes at startup; `args` are the program arguments. |
| 20 | Idempotency guard: if the table already has rows, skip. Without this, every restart would insert four more stations (the H2 file persists!). |
| 21 | Log message proving the guard ran. |
| 22 | `return` exits the lambda early. |
| 23 | End of the guard. |
| 24 | `repository.saveAll(...)` — saves every element; Spring Data issues one INSERT per entity (it can batch if configured). |
| 25 | Station 1: Tunis. `.builder()` (Lombok) then setters in fluent form, `.build()` allocates the object. No id is set → each INSERT gets a generated id. |
| 26 | Station 2: Sousse. |
| 27 | Station 3: Sfax. Note the station is called "Station Melki" but the city is Sfax — the city/governorate fields are what matter for lookups. |
| 28 | Station 4: Gabès. |
| 29 | Closes `saveAll`. |
| 30 | Confirmation log. |
| 31–33 | Closes lambda, method and class. |

## When exactly does it run?

```
... Spring context ready
... Tomcat started on port 8082
... Running CommandLineRunner seedStations
... Seeded 4 stations: Tunis, Sousse, Sfax, Gabès.
... Started StationServiceApplication
```

`CommandLineRunner` beans run **after** the context is fully initialised — the datasource,
Hibernate, `ddl-auto` and the repositories are ready. That is why the table already exists
when `saveAll` executes.

If seeding throws, Spring Boot logs `Application run failed` and shuts down (exit code 1).

## Idempotency and the H2 file database

- First run: `count() == 0` → insert 4 rows.
- Every later run: the file `./data/station-db.mv.db` still contains the rows → `count()` > 0 → skip.
- Rows deleted by hand through `/stations/{id}`: the seed is **not** re-run for the missing
  ones, because the guard only checks whether the table is empty.
- To reseed from scratch: stop the service, delete the `data/` folder, start again.

⚠️ The relative path `./data/...` depends on the working directory. If you run the jar from
a different folder, a **new empty database** is created there and the seeder runs again.

## Why a `CommandLineRunner` and not `data.sql`?

| Approach | Pros | Cons |
|---|---|---|
| `CommandLineRunner` (this project) | Type-safe, uses the repository, can contain logic (ids, conditions, logging), easy to read | Slightly more code |
| `data.sql` | Zero Java | Runs at schema-creation time, SQL dialect-specific, no guard unless you write `MERGE`/`INSERT ... WHERE NOT EXISTS` |
| Flyway/Liquibase | Versioned, production-grade | Overkill for a demo |

## Notes

- The ids generated are `1, 2, 3, 4` on a **fresh** database — this is what
  `trip-service`'s seeder assumes (`departureStationId` 1–4).
- There is no `existsByName` guard, so if you ever add stations by API **before** the first
  seed... you cannot: the seeder runs before you can call the API. The only risk is a
  partially populated DB (e.g. one station inserted on a previous run), in which case the
  guard skips and you get 1 station instead of 4.
- Logging goes through SLF4J; the default Spring Boot pattern prints it to the console.
