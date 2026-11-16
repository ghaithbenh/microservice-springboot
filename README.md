# Louage Tracker (JEE2 mini-project)

Microservices with Spring Boot 3.4.5 + Spring Cloud 2024.0.1, Java 21.

| Module | Port | Role |
|---|---|---|
| eureka-server | 8761 | Service registry (done) |
| auth-service | 8084 | Users, registration and JWT login (done) |
| api-gateway | 8080 | Single entry point, `lb://` routes + JWT security (done) |
| louage-service | 8081 | Louages CRUD (done) |
| station-service | 8082 | Stations CRUD (done) |
| trip-service | 8083 | Orchestrator with Feign + @Transient (done) |

## Open in IntelliJ
File > Open > select the `louage-tracker` folder (the one with the parent pom.xml).
Project SDK = 21. Then click "Load Maven Changes".

## Start order
1. eureka-server  -> http://localhost:8761
2. auth-service   -> http://localhost:8084
3. louage-service, station-service
4. trip-service
5. api-gateway

## Run with Docker Compose

Requirements: Docker with Docker Compose v2.

```bash
docker compose up --build
```

This builds the 6 local images and starts, in order:

| Service | URL |
|---|---|
| eureka-server | http://localhost:8761 |
| auth-service | http://localhost:8084 |
| api-gateway | http://localhost:8080 |
| louage-service | http://localhost:8081 |
| station-service | http://localhost:8082 |
| trip-service | http://localhost:8083 |

The gateway entry point is:

```text
http://localhost:8080/api/trips/1
```

Each business service stores its H2 database in a named volume
(`louage-data`, `station-data`, `trip-data`, `auth-data`). Stop the stack with:

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
http://localhost:8083/actuator/prometheus. Prometheus scrapes all six
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

## Security

Authentication is handled by `auth-service`, and every request that reaches
the gateway (except the public paths below) must carry a valid HS256 JWT.

Flow:

1. `POST /api/auth/register` creates a `DRIVER` or `PASSENGER` account.
2. `POST /api/auth/login` checks the BCrypt password hash and returns a JWT
   whose subject is the username, whose `role` claim is `ADMIN`, `DRIVER` or
   `PASSENGER`, and which expires after one hour.
3. The caller sends `Authorization: Bearer <token>` on every other request.
4. The gateway validates the signature/expiry and forwards the header; each
   business service validates it again as an OAuth2 resource server and maps
   the `role` claim to `ROLE_<role>` for method security.

The signing secret comes from `app.jwt.secret`, overridden by the
`APP_JWT_SECRET` environment variable. Compose passes the same value to the
gateway, auth-service and the three business services. The development default
is `louage-tracker-dev-secret-key-32-chars-min`; set a strong value in any
real deployment.

Seeded users (created on first start of auth-service):

| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `driver` | `driver123` | DRIVER |
| `passenger` | `passenger123` | PASSENGER |

Permission table:

| Endpoint | ADMIN | DRIVER | PASSENGER | Anonymous |
|---|---|---|---|---|
| `GET` on any business endpoint | yes | yes | yes | no (401) |
| `POST` / `PUT` / `DELETE` `/louages/**` | yes | no (403) | no (403) | no (401) |
| `POST` / `PUT` / `DELETE` `/stations/**` | yes | no (403) | no (403) | no (401) |
| `POST` / `PUT` `/trips/**` | yes | yes | no (403) | no (401) |
| `DELETE /trips/{id}` | yes | no (403) | no (403) | no (401) |
| `POST /trips/{id}/join` | yes | no (403) | yes | no (401) |
| `POST /auth/register`, `POST /auth/login` | public | public | public | public |
| Swagger, `/actuator/health`, `/actuator/prometheus` | public | public | public | public |

Sample login and authenticated call through the gateway:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r .token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/trips
```

A request without a token returns a JSON `401`, and a valid token with the
wrong role returns a JSON `403`. When trip-service calls louage-service or
station-service through Feign, it forwards the incoming `Authorization`
header, so the partner services see the same user. A `401`/`403` from a
partner is surfaced as an authorization error, not as a service outage.

## Swagger (once controllers exist)
- http://localhost:8081/swagger-ui.html
- http://localhost:8082/swagger-ui.html
- http://localhost:8083/swagger-ui.html
- http://localhost:8084/swagger-ui.html

Use the **Authorize** button and paste the JWT (without the `Bearer ` prefix).

## Gateway routes
- /api/auth/**     -> auth-service
- /api/louages/**  -> louage-service
- /api/stations/** -> station-service
- /api/trips/**    -> trip-service

## Your TODO
1. louage-service: Louage entity, repository, controller (/louages)
2. station-service: Station entity, repository, controller (/stations)
3. trip-service: Trip entity with @Transient fields, 2 Feign clients, fallbacks, controller (/trips)
4. Business rule: seat counter, DEPARTED when full
5. Sample data (CommandLineRunner) in each service
