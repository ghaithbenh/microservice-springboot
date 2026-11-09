# station-service — package walkthrough

> Port **8082** · Root package `tn.kira.louage.stationservice`
> Role: CRUD over **louage stations** (name, city, governorate), stored in its own H2 database.

This is the simplest business service — the best module to read first. It is a textbook
three-layer Spring application:

```
HTTP request
   │
   ▼
controller/StationController   ← URL mapping, HTTP status codes, JSON in/out
   │  (calls)
   ▼
repository/StationRepository  ← Spring Data proxy, no implementation written by hand
   │  (JPA / Hibernate)
   ▼
entity/Station                 ← mapped to the "stations" table
   │
   ▼
H2 file database  ./data/station-db.mv.db
```

Configuration lives in `config/DataSeeder` (sample data) and `StationServiceApplication`
(bootstrap).

## Package layout

```
station-service/
├── pom.xml
└── src/main/
    ├── java/tn/kira/louage/stationservice/
    │   ├── StationServiceApplication.java
    │   ├── config/DataSeeder.java
    │   ├── controller/StationController.java
    │   ├── entity/Station.java
    │   └── repository/StationRepository.java
    └── resources/application.yml
```

| Package | Responsibility | Depends on |
|---|---|---|
| `entity` | JPA mapping, database schema | Jakarta Persistence, Lombok |
| `repository` | Data access | Spring Data JPA |
| `controller` | HTTP API | entity, repository, springdoc |
| `config` | Seed data at startup | entity, repository |

🔍 Dependency direction: `controller → repository → entity`. The entity knows nothing about
the repository, and the repository knows nothing about HTTP. This one-way flow is what keeps
each piece testable.

## `pom.xml` line by line

```xml
 7:     <parent>
 8:         <groupId>tn.kira</groupId>
 9:         <artifactId>louage-tracker</artifactId>
10:         <version>1.0.0</version>
11:         <relativePath>../pom.xml</relativePath>
12:     </parent>
14:     <artifactId>station-service</artifactId>
```

| Lines | Dependency | Why it is needed |
|---|---|---|
| 7–12 | parent | Inherit Java 21, Spring Boot 3.4.5 versions, Spring Cloud BOM. |
| 14 | artifactId | Module name → `station-service-1.0.0.jar`. |
| 17–20 | `spring-boot-starter-web` | Embedded Tomcat, Spring MVC (`@RestController`, `@GetMapping`...), Jackson (JSON serialization), validation infrastructure. |
| 21–24 | `spring-boot-starter-data-jpa` | Spring Data repositories + Hibernate ORM + transaction management + HikariCP connection pool. |
| 25–29 | `com.h2database:h2` (`runtime`) | The database driver. `runtime` = not needed at compile time (you never import H2 classes); it is only present when the app runs. |
| 30–33 | `spring-cloud-starter-netflix-eureka-client` | Registers this app in Eureka and downloads the registry. |
| 34–38 | `springdoc-openapi-starter-webmvc-ui` | Generates the OpenAPI document from the controllers and serves Swagger UI. Version comes from the parent property `${springdoc.version}`. |
| 39–43 | `lombok` (`optional`) | Compile-time code generator. `optional=true` means dependent projects do not inherit it. |
| 44–48 | `spring-boot-starter-test` (`test`) | JUnit 5, Mockito, AssertJ, `@SpringBootTest`. |
| 51–66 | `spring-boot-maven-plugin` | Repackages into an executable jar and **excludes Lombok** from the fat jar (it is not needed at runtime because it already generated the bytecode). |

## `application.yml` line by line

```yaml
 1: server:
 2:   port: 8082
 4: spring:
 5:   application:
 6:     name: station-service
 7:   datasource:
 8:     url: jdbc:h2:file:./data/station-db;AUTO_SERVER=TRUE   # own database
 9:     username: sa
10:     password:
11:   jpa:
12:     hibernate:
13:       ddl-auto: update
14:     show-sql: true
15:   h2:
16:     console:
17:       enabled: true
19: eureka:
20:   client:
21:     service-url:
22:       defaultZone: http://localhost:8761/eureka
```

| Lines | Explanation |
|---|---|
| 1–2 | HTTP port 8082. |
| 4–6 | Service name registered in Eureka. ⚠️ Must equal the `lb://station-service` URI in the gateway, otherwise routing fails with 503. |
| 7–10 | DataSource. `jdbc:h2:file:./data/station-db` → an H2 database persisted in the file `./data/station-db.mv.db`, relative to the working directory (IntelliJ: the module folder by default). `AUTO_SERVER=TRUE` lets several connections share the file. `sa` / empty password are H2 defaults. |
| 11–14 | JPA/Hibernate. `ddl-auto: update` → on every start, Hibernate creates missing tables and columns but never drops anything. `show-sql: true` → prints formatted SQL to the console (handy for learning, noisy in production). |
| 15–17 | Enables the H2 web console at `http://localhost:8082/h2-console`. Log in with the same JDBC URL and `sa` / empty. |
| 19–22 | Where to find Eureka. `defaultZone` is the conventional zone name. |

## The request flow for `GET /stations/2`

1. Tomcat receives the request.
2. `DispatcherServlet` asks the handler mapping for a match: `StationController.getOne`.
3. `@PathVariable Long id` converts `"2"` → `Long 2`.
4. `repository.findById(2)` is a proxy call → Hibernate issues
   `select ... from stations where id=?`.
5. The `Optional<Station>` is mapped: present → `ResponseEntity.ok(station)`.
6. Jackson serializes `Station` to JSON (`{"id":2,...}`).
7. Spring writes status 200 + `Content-Type: application/json` + body.

## Files in this package

| File | What it contains |
|---|---|
| [StationServiceApplication.md](StationServiceApplication.md) | Bootstrap class |
| [entity-Station.md](entity-Station.md) | JPA entity, table mapping |
| [repository-StationRepository.md](repository-StationRepository.md) | Spring Data interface |
| [controller-StationController.md](controller-StationController.md) | All five REST endpoints |
| [config-DataSeeder.md](config-DataSeeder.md) | Sample data on startup |
