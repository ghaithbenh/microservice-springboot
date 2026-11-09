# DataSeeder (louage-service)

> File: `louage-service/src/main/java/tn/kira/louage/louageservice/config/DataSeeder.java`
> Package: `tn.kira.louage.louageservice.config`
> Type: `@Configuration` + `@Bean CommandLineRunner`
> Status: ⚠️ **does not compile** (missing receiver on line 24)

## What it is supposed to do

On startup, if the `louages` table is empty, insert three sample louages.

## The code as it exists

```java
 1: package tn.kira.louage.louageservice.config;
 2:
 3: import java.util.List;
 4:
 5: import org.springframework.boot.CommandLineRunner;
 6: import org.springframework.context.annotation.Bean;
 7: import org.springframework.context.annotation.Configuration;
 8:
 9: import lombok.extern.slf4j.Slf4j;
10: import tn.kira.louage.louageservice.entity.Louage;
11: import tn.kira.louage.louageservice.repository.LouageRepository;
12:
13: @Configuration
14: @Slf4j
15: public class DataSeeder {
16:
17:     @Bean
18:     CommandLineRunner seedLouages(LouageRepository repository) {
19:         return args -> {
20:             if (repository.count() > 0) {
21:                 log.info("Louages already seeded, skipping.");
22:                 return;
23:             }
24:              .saveAll(List.of(                                  // ❌ BROKEN
25:                     Louage.builder().plateNumber("123TUN4567").capacity(9).driverName("Ali Ben Salah").status(Louage.Status.AVAILABLE).build(),
26:                     Louage.builder().plateNumber("987TUER3210").capacity(8).driverName("Mehdi Trabelsi").status(Louage.Status.AVAILABLE).build(),
27:                     Louage.builder().plateNumber("145TUET7890").capacity(9).driverName("Sami Gharbi").status(Louage.Status.ON_ROAD).build()
28:             ));
29:             log.info("Seeded 3 louages.");
30:         };
31:     }
32: }
```

## The bug

Line 24 starts with `.saveAll(...)`. In Java, a method invocation without an explicit
receiver needs to be a bare name (`saveAll(...)`, only valid inside a class that has that
method) or must continue an expression (`repository.saveAll(...)`). A leading dot on a fresh
line has nothing to attach to, so `javac` reports something like:

```
error: illegal start of expression
             .saveAll(List.of(
             ^
```

(The exact message varies by compiler; IntelliJ shows "Identifier expected".)

### The fix

```java
24:             repository.saveAll(List.of(
```

That is the only change needed. The `target/classes/.../DataSeeder.class` file in the repo
was produced **before** this typo was introduced (or by a different copy of the source), so
a stale class exists while the current source cannot be compiled — `mvn clean` removes it.

## Line by line (as intended, with the fix applied)

| Lines | Explanation |
|---|---|
| 1 | Config package. |
| 3 | `List` import for `List.of(...)`. |
| 5–7 | `CommandLineRunner` + `@Bean` + `@Configuration`. |
| 9 | Lombok `@Slf4j` → `log` field. |
| 10–11 | Entity and repository. |
| 13 | `@Configuration` — bean-definition source. |
| 14 | `@Slf4j` — logger. |
| 15 | Class declaration. |
| 17 | `@Bean` — the method's return value becomes a bean of type `CommandLineRunner`. Method name `seedLouages` = bean name. |
| 18 | Parameter `LouageRepository repository` is injected by Spring (it resolves the repository proxy). |
| 19 | Lambda implementing `CommandLineRunner.run(String... args)`. |
| 20 | Guard: only seed when the table is empty (the H2 file persists across restarts). |
| 21–22 | If already seeded: log and `return` from the lambda. |
| 23 | End guard. |
| 24 | `repository.saveAll(...)` — INSERT each entity. With the typo it is invalid Java. |
| 25 | Louage 1 — plate `123TUN4567`, capacity 9, driver Ali Ben Salah, `AVAILABLE`. |
| 26 | Louage 2 — plate `987TUER3210`, capacity 8, driver Mehdi Trabelsi, `AVAILABLE`. |
| 27 | Louage 3 — plate `145TUET7890`, capacity 9, driver Sami Gharbi, `ON_ROAD`. |
| 28 | Closes `List.of` and `saveAll`. |
| 29 | Success log. |
| 30–32 | Closes the lambda, method and class. |

## Why the seeder matters for `trip-service`

`trip-service`'s seeder references `louageId` 1 and 2 and station ids 1–4. On a fresh
environment the insertion order here gives the sample louages ids 1, 2, 3 — so the trip seed
data "points" at real (but unverified) louages. Because each service has its own database,
there is no foreign key: nothing enforces this relationship, and nothing breaks if the ids do
not exist.

## Behaviour after the fix

| Run | Result |
|---|---|
| First start, empty DB | inserts 3 louages, ids 1–3 |
| Second start | `count() > 0` → "Louages already seeded, skipping." |
| DB deleted (`data/` removed) | seeds again |
| DB contains 1 row only | guard skips → stays at 1 row |

## Possible improvements (teaching notes)

```java
@Bean
CommandLineRunner seedLouages(LouageRepository repository) {
    return args -> {
        List<Louage> samples = List.of(...);
        samples.stream()
               .filter(l -> !repository.existsByPlateNumber(l.getPlateNumber()))
               .forEach(repository::save);     // per-row idempotency
        ...
    };
}
```

This variant uses the otherwise-unused `existsByPlateNumber` and survives partial data.
For production, prefer Flyway/Liquibase migrations over runtime seeders.
