# EurekaServerApplication

> File: `eureka-server/src/main/java/tn/kira/louage/eureka/EurekaServerApplication.java`
> Package: `tn.kira.louage.eureka`
> Type: Spring Boot bootstrap class (application entry point)

## The code

```java
 1: package tn.kira.louage.eureka;
 2:
 3: import org.springframework.boot.SpringApplication;
 4: import org.springframework.boot.autoconfigure.SpringBootApplication;
 5: import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;
 6:
 7: @SpringBootApplication
 8: @EnableEurekaServer
 9: public class EurekaServerApplication {
10:     public static void main(String[] args) {
11:         SpringApplication.run(EurekaServerApplication.class, args);
12:     }
13: }
```

## Line by line

| Lines | Explanation |
|---|---|
| 1 | Declares the package. It also defines the **component-scan root**: `@SpringBootApplication` only scans `tn.kira.louage.eureka` and below. |
| 3–5 | Imports: `SpringApplication` (the bootstrap helper), `@SpringBootApplication` (the meta-annotation), `@EnableEurekaServer` (switches on the registry). |
| 7 | `@SpringBootApplication` = configuration class + auto-configuration + component scan. Auto-configuration is what detects the Eureka Server classes on the classpath and wires the registry for you. |
| 8 | `@EnableEurekaServer` imports the Eureka Server configuration (`EurekaServerAutoConfiguration`): it enables the `/eureka/**` REST endpoints, the dashboard, peer awareness, and the instance registry. Without this line the app would start as an ordinary Eureka **client** (or fail, since no registry URL is configured). |
| 9 | Class declaration. It has no fields or methods besides `main` — the class is only a configuration anchor. |
| 10 | Standard JVM entry point. `args` are the command-line arguments. |
| 11 | `SpringApplication.run(...)` creates the `ApplicationContext`, starts the embedded Tomcat on port 8761 (read from `application.yml`), and blocks until the app shuts down. |
| 12–13 | Closing braces. |

## What happens in `SpringApplication.run`

1. Create the `ApplicationContext` (an `AnnotationConfigServletWebServerApplicationContext`).
2. Run `EnvironmentPostProcessor`s and load `application.yml`.
3. Perform component scanning from package `tn.kira.louage.eureka`.
4. Apply auto-configurations, including `EurekaServerAutoConfiguration` activated by
   `@EnableEurekaServer`.
5. Start the embedded servlet container (Tomcat) on 8761.
6. Log `Started EurekaServerApplication in X seconds`.
7. Keep running (non-daemon threads).

## How to run

```bat
:: from the project root
mvn -pl eureka-server spring-boot:run

:: or after packaging
java -jar eureka-server/target/eureka-server-1.0.0.jar
```

Then open http://localhost:8761. Until other services start, the dashboard shows
"No instances available".

## Notes

- The package name is `tn.kira.louage.eureka` — **not** `...louageservice`. Each module has
  its own root package, which keeps component scanning inside its own module.
- There is no `@EnableDiscoveryClient` here: a server does not discover itself, and the
  starter auto-configures the client side anyway.
