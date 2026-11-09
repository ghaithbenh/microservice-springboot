# StationServiceApplication

> File: `station-service/src/main/java/tn/kira/louage/stationservice/StationServiceApplication.java`
> Package: `tn.kira.louage.stationservice`
> Type: Spring Boot bootstrap class

## The code

```java
 1: package tn.kira.louage.stationservice;
 2:
 3: import org.springframework.boot.SpringApplication;
 4: import org.springframework.boot.autoconfigure.SpringBootApplication;
 5:
 6: @SpringBootApplication
 7: public class StationServiceApplication {
 8:     public static void main(String[] args) {
 9:         SpringApplication.run(StationServiceApplication.class, args);
10:     }
11: }
12: // TODO: entity Station, StationRepository, StationController (CRUD under /stations)
```

## Line by line

| Lines | Explanation |
|---|---|
| 1 | Package = this module's component-scan root. Because the class lives here, `config`, `controller`, `entity` and `repository` (all sub-packages) are scanned. |
| 3–4 | Imports: `SpringApplication` and `@SpringBootApplication`. |
| 6 | `@SpringBootApplication` enables auto-configuration. On the classpath it finds: Spring MVC + Tomcat (from `starter-web`), Hibernate + Spring Data (from `starter-data-jpa`), the H2 driver, the Eureka client, and springdoc. All are configured from `application.yml` and sensible defaults. |
| 7 | Class declaration; the only method is `main`. |
| 8 | JVM entry point. |
| 9 | Boots the context: creates beans (`StationController`, the repository proxy, `DataSeeder`, ...), connects to H2, runs `ddl-auto`, starts Tomcat on 8082, then executes `CommandLineRunner`s. |
| 10–11 | Closing braces. |
| 12 | **Stale comment.** The TODO was written before the module was implemented; all three classes now exist. Safe to delete, but it is harmless. |

## Beans created because of this class

| Bean | Created by | Lifecycle |
|---|---|---|
| `StationController` | Component scan (`@RestController`) | Singleton |
| `stationRepository` | Spring Data, from the interface | Singleton proxy |
| `entityManagerFactory` | Hibernate auto-configuration | Singleton |
| `dataSource` | HikariCP auto-configuration, using the YAML URL | Singleton |
| `seedStations` CommandLineRunner | `DataSeeder.@Bean` | Executed once after startup |
| `openApiResource` / Swagger UI | springdoc auto-configuration | Served at `/v3/api-docs` |

## Startup order (important for beginners)

1. Read `application.yml`.
2. Create the DataSource and EntityManagerFactory.
3. Hibernate runs `ddl-auto: update` → creates `stations` if missing.
4. Scan and instantiate `@RestController`/`@Repository`/`@Configuration` classes.
5. Register with Eureka (asynchronously; failure to reach Eureka does **not** stop startup).
6. Start Tomcat on 8082.
7. Run the `CommandLineRunner` → seeds 4 stations the first time.
8. Log `Started StationServiceApplication`.

## How to run

```bat
mvn -pl station-service spring-boot:run
:: or
java -jar station-service/target/station-service-1.0.0.jar
```

Then:
- http://localhost:8082/stations
- http://localhost:8082/swagger-ui.html
- http://localhost:8082/h2-console
