# 01 — Architecture

## What the application does

Louage Tracker manages Tunisian "louages" (shared intercity taxis):

- a **louage** is a vehicle with a plate number, a capacity and a driver;
- a **station** is a departure/arrival point (name, city, governorate);
- a **trip** links one louage + one departure station + one destination station,
  with a seat counter, a price per seat and a status.

The system is split into **five independent Spring Boot applications**. Each one is its own
process with its own embedded web server and its own H2 database file.

## The five modules

```
                    ┌──────────────────────────────┐
   browser / curl   │        api-gateway           │  :8080
   ────────────────►│  /api/**  ──►  lb://service  │
                    └───────────┬──────────────────┘
                                │ asks Eureka:
                                │ "where is louage-service?"
                                ▼
                    ┌──────────────────────────────┐
                    │        eureka-server         │  :8761
                    │  registry of live instances  │
                    └──▲────────▲────────▲─────────┘
                       │        │        │  all services register here
          ┌────────────┘        │        └──────────────┐
          │                     │                       │
┌─────────┴────────┐  ┌─────────┴────────┐   ┌──────────┴──────────┐
│  louage-service  │  │ station-service  │   │    trip-service     │
│      :8081       │  │      :8082       │   │       :8083         │
│  H2: louage-db   │  │  H2: station-db  │   │  H2: trip-db        │
└──────────────────┘  └──────────────────┘   └─────────────────────┘
        ▲                    ▲                        │
        └────────────────────┴────────────────────────┘
                 intended: Feign calls from trip-service
                 to fetch louage/station details (NOT implemented yet)
```

| Module | Port | Owns | Depends on |
|---|---|---|---|
| `eureka-server` | 8761 | Service registry | nothing |
| `api-gateway` | 8080 | Public entry point, routing | Eureka |
| `louage-service` | 8081 | `louages` table | Eureka |
| `station-service` | 8082 | `stations` table | Eureka |
| `trip-service` | 8083 | `trips` table | Eureka, (future: Feign → louage + station) |

## Why microservices here?

Instead of one application with three tables, each domain is isolated:

- **Independent data ownership.** `trip-service` cannot read the `louages` table directly;
  it stores a `louageId` and is supposed to ask `louage-service` over HTTP.
- **Independent deployment.** You can restart or replace one service without rebuilding the others.
- **One public door.** Clients never call `:8081/. :8082/. :8083` directly in production;
  they call the gateway on `:8080`.

## The three infrastructure ideas you must understand

### 1. Service discovery (Eureka)

Each service, at startup, sends a heartbeat to `http://localhost:8761/eureka` saying
"`louage-service` is alive at `192.168.x.x:8081`". The registry keeps that table.
When a service dies, its heartbeats stop and Eureka removes it after a while.

`eureka-server` itself has `register-with-eureka: false` and `fetch-registry: false`
because a registry does not need to register with itself.

### 2. Client-side load balancing (`lb://`)

In `api-gateway/src/main/resources/application.yml`:

```yaml
uri: lb://louage-service
```

`lb://` means: *do not connect to a hard-coded host*. Ask Eureka for the instances of
`louage-service`, pick one (round-robin by default via Spring Cloud LoadBalancer), and
forward the request there. If you start two copies of `louage-service` on different ports,
the gateway automatically spreads traffic between them.

### 3. Declarative HTTP clients (Feign) — configured but not used yet

`trip-service` has `@EnableFeignClients` and the OpenFeign + Resilience4j dependencies,
and its YAML defines timeouts and circuit-breaker thresholds. But **no `@FeignClient`
interface exists yet**, so no cross-service call actually happens. See
[trip-service/README.md](trip-service/README.md).

## Database design

Each service uses an **H2 file database**, not an in-memory one:

```yaml
url: jdbc:h2:file:./data/louage-db;AUTO_SERVER=TRUE
```

- `file:` → data survives a restart, written under `./data/`.
- `AUTO_SERVER=TRUE` → the file can be opened by more than one JVM/connection at once.
- `ddl-auto: update` → Hibernate compares entities to tables and adds missing columns/tables.
- `show-sql: true` → every generated SQL statement is printed to the console.
- H2 web console: `http://localhost:8081/h2-console` (JDBC URL `jdbc:h2:file:./data/louage-db`, user `sa`, empty password).

**Important consequence:** there are **no foreign keys between services**. `trip.louageId`
is just a number. The database will happily store `louageId = 999` even if no such louage
exists. Consistency is the job of the application layer (which currently does not check).

## Startup order

Eureka first, then the services, then the gateway:

1. `eureka-server` → open http://localhost:8761 and check the dashboard.
2. `louage-service` and `station-service` (order between them does not matter).
3. `trip-service`.
4. `api-gateway`.

Starting a client before Eureka is not fatal — it retries — but the gateway may return
`503 Service Unavailable` for the first seconds until its local registry copy is refreshed.

## What happens on a real request

`GET http://localhost:8080/api/louages/1`:

1. The gateway matches `Path=/api/louages/**` and the `louage-service` route.
2. `RewritePath` turns `/api/louages/1` into `/louages/1`.
3. `lb://louage-service` is resolved through Eureka to `localhost:8081`.
4. `LouageController.getOne(1)` runs, `LouageRepository.findById(1)` runs a SQL `select`.
5. The `Louage` object is serialized to JSON by Jackson and sent back through the gateway.

The same request directly to `http://localhost:8081/louages/1` skips steps 1–3.

## Security / production topics that are intentionally absent

This is a teaching mini-project. It has no authentication, no HTTPS, no centralized
configuration server, no distributed tracing, no Docker setup, and H2 (not PostgreSQL).
