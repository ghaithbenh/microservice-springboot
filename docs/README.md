# Louage Tracker — Full Code Walkthrough

This `docs/` folder explains **every class, every package, and every configuration file**
in the Louage Tracker project, line by line. It is written for someone who knows a little
Java and wants to understand what each line actually does at runtime.

> Project root: `louage-tracker/` (the folder that contains the parent `pom.xml`).
> Java 21 · Spring Boot 3.4.5 · Spring Cloud 2024.0.1.

---

## How to read this documentation

| Order | Document | What you learn |
|---|---|---|
| 1 | [01-architecture.md](01-architecture.md) | The big picture: 5 services, ports, who talks to whom |
| 2 | [02-parent-pom.md](02-parent-pom.md) | How Maven builds the 5 modules |
| 3 | [api-reference.md](api-reference.md) | Every HTTP endpoint and example calls |
| 4 | [glossary.md](glossary.md) | Every annotation used in the project, grouped by job |
| 5 | Module docs (below) | Line-by-line walkthrough of each file |

## Module documents

| Module | Port | Package doc | Classes documented |
|---|---|---|---|
| eureka-server | 8761 | [eureka-server/README.md](eureka-server/README.md) | `EurekaServerApplication` |
| api-gateway | 8080 | [api-gateway/README.md](api-gateway/README.md) | `ApiGatewayApplication` |
| louage-service | 8081 | [louage-service/README.md](louage-service/README.md) | `LouageServiceApplication`, `Louage`, `LouageRepository`, `LouageController`, `DataSeeder` |
| station-service | 8082 | [station-service/README.md](station-service/README.md) | `StationServiceApplication`, `Station`, `StationRepository`, `StationController`, `DataSeeder` |
| trip-service | 8083 | [trip-service/README.md](trip-service/README.md) | `TripServiceApplication`, `Trip`, `TripRepository`, `TripController`, `DataSeeder` |

Each module README also explains that module's `pom.xml` and `application.yml` line by line.

## Conventions used in these documents

- **Line numbers** refer to the source file as it exists today. If you edit a file, the
  numbers may drift; the explanation itself stays valid.
- Code shown in tables is the *actual* code, so you can search for it in IntelliJ.
- 🔍 = "why does this matter" note.
- ⚠️ = bug, trap, or unfinished feature found while documenting.

## Project status (what is done vs. not done)

The README of the project lists a TODO list. Here is the honest status after reading the code:

| Feature | Status |
|---|---|
| Service registry (Eureka) | ✅ Done |
| API gateway with `lb://` routes | ✅ Done |
| louage-service CRUD | ✅ Done (⚠️ `DataSeeder` currently does not compile) |
| station-service CRUD | ✅ Done |
| trip-service CRUD | ✅ Done, but only stores foreign **ids** |
| `@Transient` enrichment fields on `Trip` | ❌ Not implemented |
| Feign clients (`LouageClient`, `StationClient`) | ❌ Not implemented (only `@EnableFeignClients` exists) |
| Fallbacks / Resilience4j usage | ❌ Configured in YAML, no code uses it yet |
| Business rule "seat counter, DEPARTED when full" | ❌ Not implemented |
| Bean validation (`@Valid`, `@NotBlank`, ...) | ❌ Not implemented |
| Global error handling (`@ControllerAdvice`) | ❌ Not implemented |

## ⚠️ Known issue found during documentation

`louage-service/.../config/DataSeeder.java` **line 24** is missing the receiver before
`.saveAll(...)`:

```java
             .saveAll(List.of(     // ❌ cannot compile: ".saveAll" has nothing to the left
```

It should be:

```java
             repository.saveAll(List.of(
```

See [louage-service/config-DataSeeder.md](louage-service/config-DataSeeder.md) for details.
The `target/classes/.../DataSeeder.class` file is a leftover from an older successful build,
which is why the compiled folder still contains a `DataSeeder.class`.

## Suggested reading path for a beginner

1. Read `01-architecture.md` (10 minutes).
2. Read `station-service` — it is the smallest CRUD module and the best template.
3. Read `louage-service` — same shape, plus an enum, a unique column, and a derived query.
4. Read `trip-service` — same CRUD shape, but it *references* other services by id.
5. Read `api-gateway` + `eureka-server` — the infrastructure around the services.
6. Keep `glossary.md` open while reading.
