# 02 — Parent POM (root `pom.xml`)

> File: `pom.xml` (project root)
> Role: **aggregator + parent** for the five modules. It contains no Java code.

## The file

```xml
 1: <?xml version="1.0" encoding="UTF-8"?>
 2: <project xmlns="http://maven.apache.org/POM/4.0.0"
 3:          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 4:          xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
 5:     <modelVersion>4.0.0</modelVersion>
 6:
 7:     <parent>
 8:         <groupId>org.springframework.boot</groupId>
 9:         <artifactId>spring-boot-starter-parent</artifactId>
10:         <version>3.4.5</version>
11:         <relativePath/>
12:     </parent>
13:
14:     <groupId>tn.kira</groupId>
15:     <artifactId>louage-tracker</artifactId>
16:     <version>1.0.0</version>
17:     <packaging>pom</packaging>
18:     <name>louage-tracker</name>
19:
20:     <properties>
21:         <java.version>21</java.version>
22:         <spring-cloud.version>2024.0.1</spring-cloud.version>
23:         <springdoc.version>2.8.6</springdoc.version>
24:     </properties>
25:
26:     <modules>
27:         <module>eureka-server</module>
28:         <module>louage-service</module>
29:         <module>station-service</module>
30:         <module>trip-service</module>
31:         <module>api-gateway</module>
32:     </modules>
33:
34:     <dependencyManagement>
35:         <dependencies>
36:             <dependency>
37:                 <groupId>org.springframework.cloud</groupId>
38:                 <artifactId>spring-cloud-dependencies</artifactId>
39:                 <version>${spring-cloud.version}</version>
40:                 <type>pom</type>
41:                 <scope>import</scope>
42:             </dependency>
43:         </dependencies>
44:     </dependencyManagement>
45: </project>
```

## Line by line

| Lines | Explanation |
|---|---|
| 1 | XML declaration; required at the top of every POM. |
| 2–4 | Opens the `<project>` element and declares the Maven XML schema, so IntelliJ can validate the file. |
| 5 | `modelVersion 4.0.0` is the only supported POM model version. Always this value. |
| 7–12 | **Parent POM.** Inheriting from `spring-boot-starter-parent` gives you: (a) curated versions for ~300 common libraries, (b) default plugin configuration (compiler, surefire, `spring-boot-maven-plugin`), (c) resource filtering and sensible encoding. `relativePath` empty means "do not search for the parent on disk; download it from the repository". |
| 14 | `groupId` — the organisation/namespace. Combined with `artifactId` it uniquely identifies the project: `tn.kira:louage-tracker`. |
| 15 | `artifactId` — the project name used in file names (e.g. `louage-tracker-1.0.0.pom`). |
| 16 | `version` — every child module inherits `1.0.0` unless it overrides it. Children do **not** declare a version. |
| 17 | `packaging=pom` — this project produces **no jar/war**. It is a container that groups modules. |
| 18 | Human-readable name shown by IDEs and Maven output. |
| 20–24 | `<properties>` are variables. `${java.version}` is read by Spring Boot's parent to configure the Java compiler → source/target 21. |
| 21 | `java.version=21` — all code is compiled for Java 21 (records, pattern matching, etc. are available). |
| 22 | `spring-cloud.version=2024.0.1` — the Spring Cloud release train that matches Spring Boot 3.4.x. Mismatched release trains are the #1 cause of weird startup failures. |
| 23 | `springdoc.version=2.8.6` — version of the OpenAPI/Swagger library. Spring Boot's parent does **not** manage springdoc, so each module that wants Swagger must declare this version explicitly. |
| 26–32 | `<modules>` is the **aggregator** feature: `mvn install` at the root builds these five folders in order. Adding a new service means creating the folder and adding one `<module>` line here. |
| 27–31 | Build order as written. There are no inter-module jar dependencies in this project, so the order is mostly cosmetic; Maven may reorder if dependencies exist. |
| 34–44 | `<dependencyManagement>` — **does not add dependencies**; it only fixes their versions. Any child can then declare `spring-cloud-starter-gateway` without a `<version>`. |
| 36–42 | Imports the Spring Cloud BOM ("bill of materials"): a POM that itself declares versions for all Spring Cloud artifacts. |
| 39 | Uses the `${spring-cloud.version}` property. |
| 40 | `type=pom` — the imported artifact is a POM, not a jar. |
| 41 | `scope=import` — only valid inside `<dependencyManagement>`; means "merge that BOM's `dependencyManagement` into mine". |
| 45 | Closes the project. |

## What this file does *not* contain

- **No `<dependencies>`.** The parent adds nothing to the classpath. Libraries are chosen
  per module (for example, only `trip-service` gets OpenFeign and Resilience4j).
- **No `<build>`.** The Spring Boot parent supplies the plugin versions and the
  `spring-boot-maven-plugin` config that each child enables.

## Why the versions matter

```
Spring Boot 3.4.5  ──┬── Java 21
                     ├── Spring Framework 6.2.x
                     ├── Hibernate 6.6.x
                     └── Spring Cloud 2024.0.1 (must match the Boot minor line)
springdoc-openapi 2.8.6 (declared manually, compatible with Boot 3.4)
```

If you upgrade Spring Boot, check
`https://spring.io/projects/spring-cloud#overview` for the matching release train.

## Common commands

| Command (run in the folder containing this `pom.xml`) | Effect |
|---|---|
| `mvn clean install` | Builds all 5 modules, runs tests, puts jars in each `target/` |
| `mvn -pl station-service spring-boot:run` | Runs one module (`-pl` = project list) |
| `mvn clean package -DskipTests` | Faster build without tests |
| `mvn dependency:tree` | Shows the real dependency graph after BOM resolution |
