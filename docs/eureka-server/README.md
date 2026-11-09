# eureka-server — package walkthrough

> Port **8761** · Java package `tn.kira.louage.eureka`
> Role: **service registry**. It knows which services are alive and at which address.

## Package layout

```
eureka-server/
├── pom.xml
└── src/main/
    ├── java/tn/kira/louage/eureka/
    │   └── EurekaServerApplication.java      ← the only class
    └── resources/
        └── application.yml
```

There is exactly one Java class. All the registry logic lives inside the
`spring-cloud-starter-netflix-eureka-server` dependency.

## `pom.xml` line by line

```xml
 7:     <parent>
 8:         <groupId>tn.kira</groupId>
 9:         <artifactId>louage-tracker</artifactId>
10:         <version>1.0.0</version>
11:         <relativePath>../pom.xml</relativePath>
12:     </parent>
14:     <artifactId>eureka-server</artifactId>
```

| Lines | Explanation |
|---|---|
| 7–12 | Inherits from the root POM (`../pom.xml`), which itself inherits from `spring-boot-starter-parent`. So this module gets Java 21, the Spring Boot dependency versions and the Spring Cloud BOM without repeating anything. |
| 14 | Module identity → jar name `eureka-server-1.0.0.jar`. `groupId` and `version` come from the parent. |
| 16–20 | `spring-cloud-starter-netflix-eureka-server` — brings in Eureka Server, the Eureka client library it uses internally, the dashboard web resources, and Jersey (the JAX-RS implementation Eureka's REST API is written with). This single dependency is the whole module. |
| 21–25 | `spring-boot-starter-test` with `scope=test` → JUnit 5, AssertJ, Mockito, Spring Test. Not packaged in the final jar. |
| 28–35 | `spring-boot-maven-plugin` — when you run `mvn package`, it repackages the jar into an **executable** ("fat") jar containing all dependencies. That is why `target/eureka-server-1.0.0.jar` can be run with `java -jar`. |

⚠️ Note that the plugin's `<configuration>` is empty here: there is no
`<excludes>` block (unlike the business services, which exclude Lombok).

## `application.yml` line by line

```yaml
 1: server:
 2:   port: 8761
 4: spring:
 5:   application:
 6:     name: eureka-server
 8: eureka:
 9:   client:
10:     register-with-eureka: false
11:     fetch-registry: false
```

| Lines | Explanation |
|---|---|
| 1–2 | The registry listens on **8761**, Eureka's conventional port. All other modules point at `http://localhost:8761/eureka`. |
| 4–6 | The application name. Not strictly needed for the server, but useful in logs. |
| 8–11 | The Eureka **client** part that is bundled inside the server. By default it would try to register with, and download, a registry — but a registry should not be its own client. Both flags are switched off to avoid startup warnings and useless network calls. |

🔍 If you forget lines 10–11, the server still works but logs errors such as
"Cannot execute request on any known server" while it tries to contact itself.

## What it does at runtime

1. Starts a web app on 8761.
2. Exposes the Eureka REST API under `/eureka/**` (what clients call).
3. Serves a human dashboard at `http://localhost:8761`.

On the dashboard you see one row per registered application, with the instance ids
(e.g. `192.168.1.20:louage-service:8081`) and their status (`UP`).

## How a client registers

When `louage-service` starts, its Eureka client:

1. `POST /eureka/apps/LOUAGE-SERVICE` with host, port, health URL, lease duration.
2. Repeats a heartbeat (`PUT .../louage-service:<id>`) every 30 s by default.
3. If the heartbeat stops, the registry evicts the instance after ~90 s
   (self-preservation mode can delay that).

## Things to remember

- Start this module **first**; everything else needs it.
- It stores the registry **in memory** — restarting it wipes registrations. Clients
  re-register automatically within ~30 s.
- Single instance, no replication, no authentication: fine for the project, not for production.
- Do **not** add `spring-boot-starter-web` here. Eureka Server brings its own web stack;
  mixing them causes conflicts.

## Files

- [EurekaServerApplication.md](EurekaServerApplication.md) — the class, line by line.
