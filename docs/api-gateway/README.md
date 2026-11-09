# api-gateway — package walkthrough

> Port **8080** · Java package `tn.kira.louage.gateway`
> Role: the **single public entry point**. It receives `/api/...` calls, rewrites the path,
> and forwards them to the right microservice discovered through Eureka.

## Package layout

```
api-gateway/
├── pom.xml
└── src/main/
    ├── java/tn/kira/louage/gateway/
    │   └── ApiGatewayApplication.java      ← the only class
    └── resources/
        └── application.yml                 ← all routing lives here
```

There is no controller, no repository, no business code. The gateway is **pure configuration**
plus one bootstrap class.

## `pom.xml` line by line

| Lines | Explanation |
|---|---|
| 7–12 | Parent = the project root POM. Inherits Java 21 and the Spring Cloud BOM. |
| 14 | Artifact id `api-gateway`. |
| 17 | Comment: *"reactive gateway (WebFlux). Do NOT add spring-boot-starter-web here"* — see the warning below. |
| 18–21 | `spring-cloud-starter-gateway` — the reactive gateway built on Spring WebFlux and Reactor Netty. It contains the routing engine, predicates and filters. |
| 22–25 | `spring-cloud-starter-netflix-eureka-client` — needed for `lb://` URIs: it registers the gateway in Eureka **and** lets it look up the other services. |
| 26–30 | Test starter (test scope). |
| 33–40 | Spring Boot Maven plugin → executable jar. |

### ⚠️ Why `spring-boot-starter-web` must stay out

`spring-boot-starter-web` pulls in **Spring MVC + Tomcat**. The gateway is **Spring WebFlux +
Reactor Netty**. Both try to auto-configure a servlet/reactive web stack; Spring Boot then
fails at startup with a message like:

```
Spring MVC found on classpath, which is incompatible with Spring Cloud Gateway.
```

If you need to call another service from inside the gateway, use `WebClient` (reactive),
not `RestTemplate`.

## `application.yml` line by line

```yaml
 1: server:
 2:   port: 8080
 4: spring:
 5:   application:
 6:     name: api-gateway
 7:   cloud:
 8:     gateway:
 9:       routes:
12:         - id: louage-service
13:           uri: lb://louage-service
14:           predicates:
15:             - Path=/api/louages/**
16:           filters:
17:             - RewritePath=/api/(?<segment>.*), /$\{segment}
18:         - id: station-service
19:           uri: lb://station-service
20:           predicates:
21:             - Path=/api/stations/**
22:           filters:
23:             - RewritePath=/api/(?<segment>.*), /$\{segment}
24:         - id: trip-service
25:           uri: lb://trip-service
26:           predicates:
27:             - Path=/api/trips/**
28:           filters:
29:             - RewritePath=/api/(?<segment>.*), /$\{segment}
31: eureka:
32:   client:
33:     service-url:
34:       defaultZone: http://localhost:8761/eureka
```

| Lines | Explanation |
|---|---|
| 1–2 | Public port **8080** — the only port clients should use. |
| 4–6 | `spring.application.name=api-gateway` is the name the gateway registers under in Eureka. |
| 7–9 | Start of the route definitions. A **route** = id + target URI + conditions (predicates) + transformations (filters). |
| 12 | Route identifier, arbitrary but must be unique. Shown in logs and the actuator endpoint. |
| 13 | Target. `lb://louage-service` = "ask the load balancer for an instance registered as `louage-service`, then send the request there". Without `lb://` you would hard-code `http://localhost:8081` and lose discovery. |
| 14–15 | **Predicate.** The route is considered only if the request path matches the Ant pattern `/api/louages/**` (`**` matches any number of segments). Conditions are ANDed if several are listed. |
| 16–17 | **Filter.** `RewritePath` applies a regex to the path. Pattern `/api/(?<segment>.*)` captures everything after `/api/` into the named group `segment`; replacement `/${segment}` rebuilds the path without the prefix. Example: `/api/louages/1` → `/louages/1`. `$\{segment}` is written with a backslash because `${...}` is Spring's property-placeholder syntax — the escape stops Spring from trying to resolve `segment` as a configuration property. |
| 18–23 | Identical route for `station-service`: `/api/stations/**` → `/stations/**` on port 8082. |
| 24–29 | Identical route for `trip-service`: `/api/trips/**` → `/trips/**` on port 8083. |
| 31–34 | Eureka client configuration: where to register and where to fetch the registry. The gateway itself is also a discovery client. |

### The regex explained step by step

```
/api/(?<segment>.*)
 │      │
 │      └── named capture group "segment": .* = the rest of the path
 └── literal "/api/"
```

| Incoming path | Captured `segment` | Rewritten path |
|---|---|---|
| `/api/louages` | `louages` | `/louages` |
| `/api/louages/1` | `louages/1` | `/louages/1` |
| `/api/stations/5/trips` | `stations/5/trips` | `/stations/5/trips` |

The `$, { }` syntax in the replacement refers to the named group. Because only the first
part of the regex is replaced, the query string (`?foo=bar`) is preserved automatically.

## Request lifecycle through the gateway

1. Reactor Netty accepts the TCP connection on 8080.
2. `RoutePredicateHandlerMapping` evaluates routes; `Path` matches.
3. Filters run in order. `RewritePath` changes the URI.
4. `ReactiveLoadBalancerClientFilter` sees `lb://` and asks
   `DiscoveryClient` (Eureka) for instances, picks one.
5. The `NettyRoutingFilter` opens an HTTP connection to the chosen instance and streams the
   request/response bodies (no buffering by default).
6. The response returns to the client. Status code and headers are forwarded unchanged.

## Testing it

```bat
:: service up?
curl.exe http://localhost:8080/api/louages

:: if you get 503, check the Eureka dashboard: is louage-service UP?
:: or bypass the gateway to prove the service itself works:
curl.exe http://localhost:8081/louages
```

## Things to remember

- The gateway does **not** transform JSON; it is a router/proxy.
- It adds no security. Any client can call any route.
- A `/api` route only exists for paths listed above — `/louages` on 8080 returns 404.
- If you restart a service, there is a short window where its old instance is still in the
  gateway's local registry cache; you may see `503` or a connection error for a few seconds.
- CORS is not configured; browser calls from another origin will be blocked unless you add
  `spring.cloud.gateway.globalcors` settings.

## Files

- [ApiGatewayApplication.md](ApiGatewayApplication.md) — the class, line by line.
