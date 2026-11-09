# ApiGatewayApplication

> File: `api-gateway/src/main/java/tn/kira/louage/gateway/ApiGatewayApplication.java`
> Package: `tn.kira.louage.gateway`
> Type: Spring Boot bootstrap class

## The code

```java
 1: package tn.kira.louage.gateway;
 2:
 3: import org.springframework.boot.SpringApplication;
 4: import org.springframework.boot.autoconfigure.SpringBootApplication;
 5:
 6: @SpringBootApplication
 7: public class ApiGatewayApplication {
 8:     public static void main(String[] args) {
 9:         SpringApplication.run(ApiGatewayApplication.class, args);
10:     }
11: }
```

## Line by line

| Lines | Explanation |
|---|---|
| 1 | Package `tn.kira.louage.gateway` — the scan root for this module. |
| 3–4 | Only two imports: the bootstrap helper and the main annotation. |
| 6 | `@SpringBootApplication` triggers auto-configuration. On this module's classpath it detects **Spring Cloud Gateway** and **Eureka client**, and builds a reactive (WebFlux/Reactor Netty) server instead of a servlet one. The routes are loaded from `spring.cloud.gateway.routes` in `application.yml`. |
| 7 | Class declaration; no fields or methods other than `main`. |
| 8 | JVM entry point. |
| 9 | Starts the application context, the reactive web server on port 8080, and keeps running. |
| 10–11 | Closing braces. |

## What is *not* here (and why)

| Missing | Why it is fine |
|---|---|
| `@EnableDiscoveryClient` | The Eureka starter auto-configures a `DiscoveryClient`; the annotation is optional in modern Spring Cloud. |
| `@EnableFeignClients` | The gateway does not use Feign. |
| `@EnableWebFlux` | Auto-configuration enables it when WebFlux is on the classpath and Spring MVC is not. |
| Any `@Bean` | Routing is entirely declarative YAML. |

## Startup log you should see

```
o.s.c.g.r.RouteDefinitionRouteLocator    : RouteDefinition matched: louage-service
o.s.c.g.r.RouteDefinitionRouteLocator    : RouteDefinition matched: station-service
o.s.c.g.r.RouteDefinitionRouteLocator    : RouteDefinition matched: trip-service
Netty started on port 8080
Started ApiGatewayApplication in 2.1 seconds
```

If those `RouteDefinition` lines are missing, the YAML indentation under
`spring.cloud.gateway.routes` is wrong.

## Common startup error

```
Spring MVC found on classpath, which is incompatible with Spring Cloud Gateway.
```

Cause: `spring-boot-starter-web` (directly or transitively) is on the classpath. Remove it —
the gateway must stay reactive. This is the reason for the comment in `pom.xml`.
