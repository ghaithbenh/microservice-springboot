# Louage Tracker (JEE2 mini-project)

Microservices with Spring Boot 3.4.5 + Spring Cloud 2024.0.1, Java 21.

| Module | Port | Role |
|---|---|---|
| eureka-server | 8761 | Service registry (done) |
| api-gateway | 8080 | Single entry point, `lb://` routes (done) |
| louage-service | 8081 | Louages CRUD (TODO) |
| station-service | 8082 | Stations CRUD (TODO) |
| trip-service | 8083 | Orchestrator with Feign + @Transient (TODO) |

## Open in IntelliJ
File > Open > select the `louage-tracker` folder (the one with the parent pom.xml).
Project SDK = 21. Then click "Load Maven Changes".

## Start order
1. eureka-server  -> http://localhost:8761
2. louage-service, station-service
3. trip-service
4. api-gateway

## Run with Docker Compose

Requirements: Docker with Docker Compose v2.

```bash
docker compose up --build
```

This builds the 5 local images and starts, in order:

| Service | URL |
|---|---|
| eureka-server | http://localhost:8761 |
| api-gateway | http://localhost:8080 |
| louage-service | http://localhost:8081 |
| station-service | http://localhost:8082 |
| trip-service | http://localhost:8083 |

The gateway entry point is:

```text
http://localhost:8080/api/trips/1
```

Each business service stores its H2 database in a named volume
(`louage-data`, `station-data`, `trip-data`). Stop the stack with:

```bash
docker compose down
```

Add `-v` to also delete the database volumes:

```bash
docker compose down -v
```

## Swagger (once controllers exist)
- http://localhost:8081/swagger-ui.html
- http://localhost:8082/swagger-ui.html
- http://localhost:8083/swagger-ui.html

## Gateway routes
- /api/louages/**  -> louage-service
- /api/stations/** -> station-service
- /api/trips/**    -> trip-service

## Your TODO
1. louage-service: Louage entity, repository, controller (/louages)
2. station-service: Station entity, repository, controller (/stations)
3. trip-service: Trip entity with @Transient fields, 2 Feign clients, fallbacks, controller (/trips)
4. Business rule: seat counter, DEPARTED when full
5. Sample data (CommandLineRunner) in each service
