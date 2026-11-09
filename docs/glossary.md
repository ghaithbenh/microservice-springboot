# Glossary — every annotation used in this project

Grouped by the job it does. Each entry says **what the annotation means at runtime**, not
just a translation of its name.

---

## 1. Bootstrapping the application

| Annotation | Used in | Meaning |
|---|---|---|
| `@SpringBootApplication` | every `*Application` class | Shortcut for three annotations: `@SpringBootConfiguration` (this class is a config source), `@EnableAutoConfiguration` (start Spring Boot's auto-configuration: web server, datasource, JPA, Jackson...), `@ComponentScan` (scan this package and all sub-packages for `@Component`, `@Service`, `@Repository`, `@RestController`, `@Configuration`). 🔍 Because the main class sits in `tn.kira.louage.louageservice`, everything under that package is discovered automatically — no XML, no explicit registration. |
| `@EnableEurekaServer` | `EurekaServerApplication` | Turns the app into a Eureka registry (it exposes the REST API and dashboard at `/`). |
| `@EnableFeignClients` | `TripServiceApplication` | Scans for interfaces annotated `@FeignClient` and creates proxy beans for them. Currently there are **no** such interfaces, so this annotation has no effect yet. |
| `@Configuration` | all `DataSeeder` classes | Marks the class as a source of bean definitions (`@Bean` methods). |

## 2. Web layer (Spring MVC)

| Annotation | Meaning |
|---|---|
| `@RestController` | `@Controller` + `@ResponseBody`. Every method's return value is serialized (by Jackson → JSON) and written to the HTTP response, instead of being interpreted as a view name. |
| `@RequestMapping("/louages")` | Base path for every handler in the class. Class-level + method-level paths are concatenated. |
| `@GetMapping` | Shortcut for `@RequestMapping(method = GET)`. |
| `@PostMapping` | Shortcut for `POST`. |
| `@PutMapping` | Shortcut for `PUT` (full replace). |
| `@DeleteMapping` | Shortcut for `DELETE`. |
| `@PathVariable Long id` | Takes the `{id}` segment from the URL path and converts it to `Long`. `GET /louages/7` → `id = 7`. |
| `@RequestBody Louage louage` | Tells Spring to read the request body and deserialize JSON into a `Louage` using Jackson. |
| `ResponseEntity<T>` | The full HTTP response: status code + headers + body. Lets the controller return `404`/`201`/`204` explicitly. Returning a plain `List<Louage>` lets Spring default to `200`. |
| `ResponseEntity.ok(body)` | `200` with a body. |
| `ResponseEntity.notFound().build()` | `404` with no body. |
| `ResponseEntity.created(uri)` | `201` plus a `Location` header. |
| `ResponseEntity.noContent().build()` | `204`, no body. Used after a successful delete. |

## 3. JPA / database mapping

| Annotation | Meaning |
|---|---|
| `@Entity` | This class is mapped to a database table; Hibernate manages it. Class must have a no-arg constructor. |
| `@Table(name = "louages")` | Overrides the default table name (which would be the class name). |
| `@Id` | This field is the primary key. |
| `@GeneratedValue(strategy = IDENTITY)` | The database generates the id (H2 `auto_increment`). Hibernate omits the id on INSERT and reads it back. `IDENTITY` fits H2/MySQL/PostgreSQL `serial`; `SEQUENCE` is the JPA-preferred alternative. |
| `@Column(...)` | Column customisation: `nullable = false` → `NOT NULL`; `unique = true` → unique constraint; `length = 20` → `varchar(20)`. These affect **DDL generation**, not Java-side validation. |
| `@Enumerated(EnumType.STRING)` | Stores the enum **name** (`"AVAILABLE"`) in the column instead of its ordinal number (`0`). 🔍 Without it (or with `ORDINAL`) reordering enum constants silently corrupts existing data. |

## 4. Spring Data repositories

| Annotation / type | Meaning |
|---|---|
| `interface XRepository extends JpaRepository<T, ID>` | Spring Data generates the implementation at startup (a JDK dynamic proxy). You write zero SQL for standard CRUD. |
| `findAll()` | `select * from ...`; returns `List<T>`. |
| `findById(id)` | `select ... where id = ?`; returns `Optional<T>` — empty if not found. |
| `save(entity)` | `insert` when the id is `null`, `update` when it is not. Returns the managed entity (with the generated id after an insert). |
| `existsById(id)` | `select count(*) > 0 ...` (or equivalent). |
| `deleteById(id)` | `delete from ... where id = ?`. Throws `EmptyResultDataAccessException` if the row is missing (which is why the controller calls `existsById` first). |
| `count()` | `select count(*)`. Used by the seeders to stay idempotent. |
| `existsByPlateNumber(String)` | **Derived query**: Spring parses the method name → `select count(*) > 0 from louages where plate_number = ?`. Property name must match the entity field. Currently unused (see `LouageRepository` doc). |

## 5. Seeding data

| Annotation | Meaning |
|---|---|
| `@Bean CommandLineRunner seedX(...)` | Registers a `CommandLineRunner` bean. Spring Boot calls `run(args)` **once, after the application context is fully initialised**, right before the app is considered started. Perfect for developers' sample data. |
| `@Slf4j` (Lombok) | Injects `private static final org.slf4j.Logger log = LoggerFactory.getLogger(TheClass.class);` so you can call `log.info(...)`. |

## 6. Lombok (compile-time code generation)

| Annotation | Generates |
|---|---|
| `@Getter` | `getId()`, `getPlateNumber()`, ... for every field. |
| `@Setter` | `setId(...)`, `setPlateNumber(...)`, ... |
| `@NoArgsConstructor` | `public Louage() {}` — **required by JPA**. |
| `@AllArgsConstructor` | `public Louage(Long id, String plateNumber, ...)` — all fields in declaration order. |
| `@Builder` | `Louage.builder().plateNumber("...").capacity(9).build()` — named, readable construction used by the seeders. |
| `@Slf4j` | Logger field named `log`. |

🔍 Lombok runs as an **annotation processor** during `javac`: the getters/setters are added to
the `.class` file, which is why IntelliJ needs the Lombok plugin to understand the code
without errors.

## 7. Swagger / OpenAPI (springdoc)

| Annotation | Meaning |
|---|---|
| `@Tag(name = "...", description = "...")` | Groups the controller's endpoints under a heading in Swagger UI. Pure documentation — no effect on routing. |
| `@Operation(summary = "...", description = "...")` | Documents one endpoint. Also documentation-only. |

The annotations produce the JSON at `/v3/api-docs`, which Swagger UI (`/swagger-ui.html`)
renders. Removing all of them would not change a single HTTP response.

## 8. Spring Cloud

| Annotation / concept | Meaning |
|---|---|
| `@EnableEurekaServer` | Start the registry. |
| `spring-cloud-starter-netflix-eureka-client` (dependency) | On startup, register this app under `spring.application.name` and fetch the registry. |
| `eureka.client.service-url.defaultZone` | URL of the registry. |
| `eureka.client.register-with-eureka: false` | Do not register this app in the registry (used by eureka-server itself). |
| `eureka.client.fetch-registry: false` | Do not download the registry (used by eureka-server itself). |
| `lb://service-name` (gateway) | Resolve instances via Eureka + Spring Cloud LoadBalancer, then proxy. |
| `Path=/api/louages/**` (gateway predicate) | Route matches when the request path matches the Ant-style pattern. |
| `RewritePath=/api/(?<segment>.*), /$\{segment}` (gateway filter) | Rewrites the path with a regex. Named group `segment` captures everything after `/api/`. `$\{segment}` is the escaped Spring placeholder syntax (otherwise Spring would try to resolve `${segment}` as a property). |
| `spring.cloud.openfeign.circuitbreaker.enabled` (YAML) | Wraps every Feign call in a Resilience4j circuit breaker, which is what makes `fallback` methods possible. |

## 9. Java language constructs worth naming

| Construct | Where | Meaning |
|---|---|---|
| `Optional<T>` | `findById` | A container that is either empty or holds one value. Forces you to handle "not found" explicitly. |
| `optional.map(fn)` | controllers | If the `Optional` has a value, apply `fn` and wrap the result; if it is empty, stay empty. |
| `optional.orElseGet(supplier)` | controllers | Return the contained value, or compute a fallback lazily (here: build a 404 response). |
| `ResponseEntity::ok` | controllers | Method reference = `x -> ResponseEntity.ok(x)`. |
| `enum` nested in entity | `Louage.Status`, `Trip.Status` | A fixed set of constants. Nested for cohesion; referenced as `Louage.Status.AVAILABLE`. |
| `String` + `double` for money | `Trip.pricePerSeat` | `double` is binary floating point — fine for a demo, but `BigDecimal` is the correct type for money in real systems. |
| `int capacity`, `int seatsTaken` | entities | Primitives cannot be `null`; a missing JSON field becomes `0`. This is why `create()` can check `<= 0` instead of `== null`. |
