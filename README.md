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

### Concurrency

`POST /trips/{id}/join` uses optimistic locking (`@Version` on `Trip`). The
louage capacity is fetched from `louage-service` **before** the database
transaction starts, and the seat increment retries up to 5 times when a
concurrent join wins the race. If every retry collides the endpoint returns
`409 Conflict`; a trip that is already full or departed still returns `400`.
This guarantees `seatsTaken` never exceeds the louage capacity.

## Redis: rate limiting and caching

Redis is used for two things:

1. **Gateway rate limiting** (`RequestRateLimiter` + `RedisRateLimiter`).
   Requests are keyed by the authenticated username, falling back to the
   client IP (`X-Forwarded-For` or the socket address) for anonymous calls.
   Swagger and actuator endpoints are not under `/api/**`, so they are exempt.
2. **Response caching** in `louage-service` and `station-service`
   (`GET /louages`, `GET /louages/{id}`, `GET /stations`, `GET /stations/{id}`).
   Entries are JSON-serialized and expire after 5 minutes. Create, update and
   delete evict the whole cache for that service. If Redis is down, a lenient
   `CacheErrorHandler` logs a warning and the request falls back to the
   database, so reads keep working.

Rate limits (per user, per route):

| Route | Replenish | Burst |
|---|---|---|
| `/api/auth/login` | 5 rps | 5 |
| `/api/**` (all other routes) | 10 rps | 20 |

When the limit is exceeded the gateway returns `429` with a JSON body:

```json
{"status":429,"error":"Too Many Requests","message":"Rate limit exceeded. Please slow down and retry."}
```

Start Redis only with `docker compose up -d redis`, or start the whole stack
with `./start-all.ps1` (it starts Redis automatically if port 6379 is closed).
Connection settings default to `localhost:6379` and can be overridden with
`SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT`.

Quick check that throttling kicks in (30 fast calls, expect some `429`s):

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"passenger","password":"passenger123"}' | jq -r .token)

for i in $(seq 1 30); do
  code=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/louages)
  echo "$i -> $code"
done
```

Quick check that an update invalidates the cache (the second `GET` shows
`"name":"Updated Station"`, never the old cached value):

```bash
curl -s -X PUT http://localhost:8080/api/stations/1 \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Updated Station","city":"Tunis","governorate":"Tunis"}'

curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/stations/1
```

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
