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

## Monitoring

The Compose stack also starts Prometheus and Grafana:

| Service | URL | Credentials |
|---|---|---|
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3000 | `admin` / `admin` |

Every service exposes its metrics at `/actuator/prometheus`, for example
http://localhost:8083/actuator/prometheus. Prometheus scrapes all five
services every 15 s using their Compose hostnames, and Grafana automatically
loads the **Louage Tracker** dashboard from
`monitoring/grafana/dashboards`.

PromQL queries used by the dashboard:

| Panel | Query |
|---|---|
| Service up/down | `up{application=~"$service"}` |
| Request rate | `sum by (application) (rate(http_server_requests_seconds_count{application=~"$service"}[5m]))` |
| Average latency | `sum by (application) (rate(http_server_requests_seconds_sum{application=~"$service"}[5m])) / sum by (application) (rate(http_server_requests_seconds_count{application=~"$service"}[5m]))` |
| p95 latency | `histogram_quantile(0.95, sum by (le, application) (rate(http_server_requests_seconds_bucket{application=~"$service"}[5m])))` |
| 5xx error rate | `sum by (application) (rate(http_server_requests_seconds_count{application=~"$service",status=~"5.."}[5m]))` |
| JVM heap used | `sum by (application) (jvm_memory_used_bytes{application=~"$service",area="heap"})` |
| Circuit breaker state | `resilience4j_circuitbreaker_state{application="trip-service"} == 1` |

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
