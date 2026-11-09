# louage-service — package walkthrough

> Port **8081** · Root package `tn.kira.louage.louageservice`
> Role: CRUD over **louages** (vehicles): plate number, capacity, driver name, status.

This module has the same three-layer shape as `station-service`, plus:

- an **enum** (`Louage.Status`) stored as a string,
- a **unique** column (`plateNumber`),
- a **derived query** (`existsByPlateNumber`) that is declared but not used,
- a **seed file that currently does not compile** (see the warning at the end).

## Package layout

```
louage-service/
├── pom.xml
└── src/main/
    ├── java/tn/kira/louage/louageservice/
    │   ├── LouageServiceApplication.java
    │   ├── config/DataSeeder.java             ⚠️ broken source (missing `repository.`)
    │   ├── controller/LouageController.java
    │   ├── entity/Louage.java
    │   └── repository/LouageRepository.java
    └── resources/application.yml
```

Communication flow:

```
HTTP ─► LouageController ─► LouageRepository (Spring Data proxy) ─► Hibernate ─► H2
                                                                     ./data/louage-db.mv.db
```

## `pom.xml`

Identical in structure to `station-service`. Key lines:

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | Tomcat + Spring MVC + Jackson |
| `spring-boot-starter-data-jpa` | Spring Data + Hibernate |
| `h2` (runtime) | Embedded database driver |
| `spring-cloud-starter-netflix-eureka-client` | Registration in Eureka |
| `springdoc-openapi-starter-webmvc-ui` | Swagger UI / OpenAPI |
| `lombok` (optional) | Getters, setters, builders, logger |
| `spring-boot-starter-test` (test) | JUnit 5 + Mockito |
| `spring-boot-maven-plugin` + Lombok exclude | Executable jar without Lombok |

No version numbers: they come from the Boot parent and the imported Spring Cloud BOM.
The only exception is springdoc, which uses `${springdoc.version}` from the root POM.

## `application.yml` line by line

```yaml
 1: server:
 2:   port: 8081
 4: spring:
 5:   application:
 6:     name: louage-service
 7:   datasource:
 8:     url: jdbc:h2:file:./data/louage-db;AUTO_SERVER=TRUE   # own database
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
| 1–2 | Port 8081. |
| 4–6 | Name registered in Eureka; must match `lb://louage-service` in the gateway. |
| 7–10 | Its **own** database file `./data/louage-db.mv.db`. Separate from station/trip DBs — no shared tables. `AUTO_SERVER=TRUE` allows a second connection (e.g. the H2 console) while the app runs. |
| 11–14 | Hibernate creates/updates the `louages` table on startup and prints SQL. |
| 15–17 | H2 web console at `http://localhost:8081/h2-console`. |
| 19–22 | Eureka registry URL. |

## What is different from station-service

| Aspect | Station | Louage |
|---|---|---|
| Enum column | none | `status` (`AVAILABLE` / `ON_ROAD`), stored as string |
| Unique constraint | none | `plateNumber` unique at the DB level |
| Custom repository method | none | `existsByPlateNumber(String)` — currently unused |
| Default value on create | none | `status` defaults to `AVAILABLE` |
| Known bug | none | `DataSeeder` missing `repository.` prefix |

## ⚠️ Compile error in `config/DataSeeder.java`

Line 24 is:

```java
             .saveAll(List.of(
```

That is not valid Java: a method call cannot start with a dot on a new line (there is no
receiver above it). It must be:

```java
             repository.saveAll(List.of(
```

The `target/classes/.../DataSeeder.class` present in the repository is a **stale artifact**
from an earlier successful build, so do not let it fool you. Running
`mvn -pl louage-service clean package` fails until this is fixed. Full explanation:
[config-DataSeeder.md](config-DataSeeder.md).

## Files in this package

| File | Content |
|---|---|
| [LouageServiceApplication.md](LouageServiceApplication.md) | Bootstrap class (stale TODO comment) |
| [entity-Louage.md](entity-Louage.md) | Entity, enum, unique plate, nullable rules |
| [repository-LouageRepository.md](repository-LouageRepository.md) | `JpaRepository` + derived query |
| [controller-LouageController.md](controller-LouageController.md) | The five endpoints and their edge cases |
| [config-DataSeeder.md](config-DataSeeder.md) | Sample data + the compile error |
