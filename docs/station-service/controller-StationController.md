# StationController

> File: `station-service/src/main/java/tn/kira/louage/stationservice/controller/StationController.java`
> Package: `tn.kira.louage.stationservice.controller`
> Type: REST controller — the HTTP layer

## What this class does

Exposes five endpoints under `/stations` and translates between HTTP and the repository:

| Method | Path | Handler | Success |
|---|---|---|---|
| GET | `/stations` | `getAll()` | 200 + JSON array |
| GET | `/stations/{id}` | `getOne(id)` | 200 + JSON object, or 404 |
| POST | `/stations` | `create(body)` | 201 + created object, `Location` header |
| PUT | `/stations/{id}` | `update(id, body)` | 200 + updated object, or 404 |
| DELETE | `/stations/{id}` | `delete(id)` | 204, or 404 |

## The code, in three parts

### Part 1 — class declaration and dependency

```java
21: @RestController
22: @RequestMapping("/stations")
23: @Tag(name = "Stations", description = "CRUD operations for louage stations")
24: public class StationController {
25:
26:     private final StationRepository repository;
27:
28:     public StationController(StationRepository repository) {
29:         this.repository = repository;
30:     }
```

| Lines | Explanation |
|---|---|
| 1–2 | `java.net.URI` (for the `Location` header) and `java.util.List`. |
| 6–14 | Spring MVC/web annotations we use. |
| 16–17 | springdoc (Swagger) annotations — documentation only. |
| 18–19 | The entity and repository this controller uses. |
| 21 | `@RestController` = `@Controller` + `@ResponseBody` → every return value is converted to JSON. Also makes the class a bean discovered by component scan. |
| 22 | Base path: all mappings below are relative to `/stations`. |
| 23 | Swagger grouping. No effect on routing. |
| 24 | Class declaration; `final` field + constructor = **constructor injection**. |
| 26 | `private final StationRepository repository;` — collaborators are immutable and non-null after construction. |
| 28–30 | Constructor. Spring sees it is the only constructor and passes the `stationRepository` bean automatically (no `@Autowired` needed in modern Spring with a single constructor). 🔍 Constructor injection is preferred over field injection because dependencies are explicit, fields can be `final`, and the class is trivially unit-testable: `new StationController(mockRepo)`. |

### Part 2 — read endpoints

```java
32:     @Operation(summary = "List all stations", ...)
34:     @GetMapping
35:     public List<Station> getAll() {
36:         return repository.findAll();
37:     }
39:     @Operation(summary = "Get one station", ...)
41:     @GetMapping("/{id}")
42:     public ResponseEntity<Station> getOne(@PathVariable Long id) {
43:         return repository.findById(id)
44:                 .map(ResponseEntity::ok)
45:                 .orElseGet(() -> ResponseEntity.notFound().build());
46:     }
```

| Lines | Explanation |
|---|---|
| 32–33 | Swagger summary/description text. |
| 34 | Handles `GET /stations`. No path value because the class already provides `/stations`. |
| 35 | Returns a plain `List<Station>` → Spring serializes it and defaults to status **200**. An empty table produces `[]`, not 404. |
| 36 | `findAll()` → `select ... from stations`. |
| 39–41 | `GET /stations/{id}` — `{id}` is a path variable. |
| 42 | `ResponseEntity<Station>` = status + headers + optional body. `@PathVariable Long id` binds and converts `"5"` to `Long 5`. If the segment is not a number, Spring returns **400** before the method runs. |
| 43 | `findById` returns `Optional<Station>`. |
| 44 | `.map(ResponseEntity::ok)`: if a station is present, wrap it in a 200 response. Method reference form of `s -> ResponseEntity.ok(s)`. |
| 45 | `.orElseGet(...)`: if the Optional is empty, call the supplier, i.e. build a `404 Not Found` with no body. (`orElse` would always build the 404 even when present; `orElseGet` is lazy.) |
| 46 | End of method. |

### Part 3 — write endpoints

```java
48:     @Operation(summary = "Create a station", ...)
50:     @PostMapping
51:     public ResponseEntity<Station> create(@RequestBody Station station) {
52:         station.setId(null);
53:         Station saved = repository.save(station);
54:         return ResponseEntity
55:                 .created(URI.create("/stations/" + saved.getId()))
56:                 .body(saved);
57:     }
59:     @Operation(summary = "Update a station", ...)
61:     @PutMapping("/{id}")
62:     public ResponseEntity<Station> update(@PathVariable Long id, @RequestBody Station input) {
63:         return repository.findById(id)
64:                 .map(existing -> {
65:                     existing.setName(input.getName());
66:                     existing.setCity(input.getCity());
67:                     existing.setGovernorate(input.getGovernorate());
68:                     return ResponseEntity.ok(repository.save(existing));
69:                 })
70:                 .orElseGet(() -> ResponseEntity.notFound().build());
71:     }
73:     @Operation(summary = "Delete a station", ...)
75:     @DeleteMapping("/{id}")
76:     public ResponseEntity<Void> delete(@PathVariable Long id) {
77:         if (!repository.existsById(id)) {
78:             return ResponseEntity.notFound().build();
79:         }
80:         repository.deleteById(id);
81:         return ResponseEntity.noContent().build();
82:     }
83: }
```

| Lines | Explanation |
|---|---|
| 50 | Handles `POST /stations`. |
| 51 | `@RequestBody` → Jackson deserializes the JSON body into a new `Station`. `id` will be `null` unless the client sent one. |
| 52 | **Force** `id = null`. Why: `save()` treats a non-null id as an UPDATE. Without this line a client could send `"id": 3` and silently overwrite station 3 by POSTing. Clearing it guarantees an INSERT. |
| 53 | `save()` → `insert into stations (city, governorate, name) values (...)`; H2 returns the generated key; the returned object has the real `id`. |
| 54–56 | `ResponseEntity.created(uri)` = status **201** + `Location` header + body. `.body(saved)` attaches the created JSON. Header value is `/stations/5` (relative). Through the gateway the public path is actually `/api/stations/5`, so the header is only strictly correct when calling the service directly. |
| 61 | `PUT /stations/{id}` — full replacement semantics in REST (here implemented as "copy all mutable fields onto the existing row"). |
| 62 | Two inputs: the id from the URL, the new values from the body. ⚠️ `input.setId(...)` is never called; the body's id is ignored on purpose. |
| 63 | Load the existing row. |
| 64–68 | If found: overwrite `name`, `city`, `governorate` with the incoming values, then `save(existing)` and return 200. Because `existing` was loaded in this transaction, `save` performs an UPDATE, not an INSERT. |
| 65–67 | ⚠️ An omitted JSON field becomes `null` and **overwrites** the current value. PUT therefore means "replace everything"; if you want partial updates, use PATCH and null checks. The station table has `nullable = false`, so a missing field causes a 500 on flush. |
| 69–70 | If not found → 404. |
| 75 | `DELETE /stations/{id}`. |
| 76 | Returns `ResponseEntity<Void>` — no body, only a status. |
| 77–79 | `existsById` first → `404` for an unknown id (a friendlier answer than letting Hibernate throw). 🔍 This is two queries (exists + delete) and has a tiny race window; alternatively call `deleteById` and catch `EmptyResultDataAccessException`. |
| 80 | `deleteById(id)` → `delete from stations where id=?`. |
| 81 | `noContent()` = **204**, the conventional response for a successful delete. |
| 82–83 | End of class. |

## Error handling: what this controller does *not* do

| Situation | Actual result |
|---|---|
| Body is invalid JSON | 400 (Spring's default `HttpMessageNotReadableException` handler) |
| `.name` is missing / null | 500 — `NOT NULL` violation at flush time |
| `id` is `"abc"` | 400 — type conversion failure |
| Database down | 500 |
| Unknown route `/station` | 404 (no handler) |
| Concurrent delete/update | last write wins; no optimistic locking (`@Version` absent) |

A production version would add `@Valid` plus constraints on the entity, and a
`@RestControllerAdvice` mapping exceptions to clean `ProblemDetail` JSON.

## Why `ResponseEntity` for some methods and plain values for others

- `getAll()` always succeeds → returning `List` is enough.
- `getOne` can fail → needs to choose 200 vs 404.
- `create` must send 201 + `Location` → needs headers and status.
- `delete` has no body → `ResponseEntity<Void>`.

## Testing it quickly

```bat
curl.exe http://localhost:8082/stations
curl.exe -X POST http://localhost:8082/stations -H "Content-Type: application/json" ^
  -d "{\"name\":\"Station Ariana\",\"city\":\"Ariana\",\"governorate\":\"Ariana\"}"
curl.exe -X PUT http://localhost:8082/stations/5 -H "Content-Type: application/json" ^
  -d "{\"name\":\"Station Ariana Centre\",\"city\":\"Ariana\",\"governorate\":\"Ariana\"}"
curl.exe -i -X DELETE http://localhost:8082/stations/5
```

Through the gateway, replace `:8082/stations` with `:8080/api/stations`.
