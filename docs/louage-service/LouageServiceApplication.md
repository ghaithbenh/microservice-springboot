# LouageServiceApplication

> File: `louage-service/src/main/java/tn/kira/louage/louageservice/LouageServiceApplication.java`
> Package: `tn.kira.louage.louageservice`
> Type: Spring Boot bootstrap class

## The code

```java
 1: package tn.kira.louage.louageservice;
 2:
 3: import org.springframework.boot.SpringApplication;
 4: import org.springframework.boot.autoconfigure.SpringBootApplication;
 5:
 6: @SpringBootApplication
 7: public class LouageServiceApplication {
 8:     public static void main(String[] args) {
 9:         SpringApplication.run(LouageServiceApplication.class, args);
10:     }
11: }
12: // TODO: entity Louage, LouageRepository, LouageController (CRUD under /louages)
```

## Line by line

| Lines | Explanation |
|---|---|
| 1 | Root package. `@SpringBootApplication` scans `tn.kira.louage.louageservice.**` — so `config`, `controller`, `entity`, `repository` are found. Note it does **not** scan `tn.kira.louage.stationservice` or other modules: each module is isolated. |
| 3–4 | Imports. |
| 6 | `@SpringBootApplication` → auto-configures MVC, JPA, H2, Eureka client, springdoc, based on the classpath and `application.yml`. |
| 7 | Class declaration. |
| 8 | JVM entry point. |
| 9 | Boots Spring: scans beans, connects to H2, runs `ddl-auto: update`, starts Tomcat on 8081, registers with Eureka, runs the `CommandLineRunner` from `DataSeeder`. |
| 10–11 | Closing braces. |
| 12 | **Stale TODO.** The entity, repository and controller already exist. It is harmless but misleading; delete it if you like. |

## Startup sequence

1. Load `application.yml` (port 8081, datasource, Eureka URL).
2. Build the HikariCP pool for `jdbc:h2:file:./data/louage-db`.
3. Hibernate creates/updates the `louages` table (`ddl-auto: update`).
4. Instantiate `LouageController`, the `LouageRepository` proxy, `DataSeeder`.
5. Start Tomcat on 8081.
6. Register with Eureka (retries in the background if it is not up yet).
7. Run `seedLouages` (currently blocked by the compile error — see below).
8. Log `Started LouageServiceApplication`.

## ⚠️ It will not build as-is

`config/DataSeeder.java` line 24 lacks the `repository.` receiver, so `javac` fails and this
main class never runs. The `target/` folder contains an old compiled copy from before the
source was broken. Fix the seeder (see [config-DataSeeder.md](config-DataSeeder.md)) before
running `mvn -pl louage-service spring-boot:run`.

## How to run (after fixing the seeder)

```bat
mvn -pl louage-service spring-boot:run
:: or
java -jar louage-service/target/louage-service-1.0.0.jar
```

- API: http://localhost:8081/louages
- Swagger: http://localhost:8081/swagger-ui.html
- H2 console: http://localhost:8081/h2-console
