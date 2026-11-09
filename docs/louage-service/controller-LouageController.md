# LouageController

> File: `louage-service/src/main/java/tn/kira/louage/louageservice/controller/LouageController.java`
> Package: `tn.kira.louage.louageservice.controller`
> Type: REST controller — HTTP layer for `/louages`

## Endpoints

| Method | Path | Handler | Success | Notes |
|---|---|---|---|---|
| GET | `/louages` | `getAll` | 200 | all rows |
| GET | `/louages/{id}` | `getOne` | 200 / 404 | |
| POST | `/louages` | `create` | 201 | defaults status to `AVAILABLE` |
| PUT | `/louages/{id}` | `update` | 200 / 404 | status only overwritten if non-null |
| DELETE | `/louages/{id}` | `delete` | 204 / 404 | |

## Class declaration and injection

```java
21: @RestController
22: @RequestMapping("/louages")
23: @Tag(name = "Louages", description = "CRUD operations for louages")
24: public class LouageController {
25:
26:     private final LouageRepository repository;
27:
28:     public LouageController(LouageRepository repository) {
29:         this.repository = repository;
30:     }
```

| Lines | Explanation |
|---|---|
| 3–4 | `URI` (Location header) and `List`. |
| 6–14 | Spring MVC annotations. |
| 16–17 | Swagger annotations (`@Operation`, `@Tag`) — documentation only. |
| 18–19 | Entity and repository imports. |
| 21 | `@RestController`: bean + JSON responses. |
| 22 | Base path `/louages`. Through the gateway this is reachable as `/api/louages`. |
| 23 | Groups endpoints under "Louages" in Swagger UI. |
| 24 | Class declaration. |
| 26 | `final` repository reference → immutable dependency. |
| 28–30 | Constructor injection: Spring finds the single constructor and supplies the `louageRepository` proxy. |

## Read endpoints

```java
32:     @Operation(summary = "List all louages", ...)
34:     @GetMapping
35:     public List<Louage> getAll() {
36:         return repository.findAll();
37:     }
39:     @Operation(summary = "Get one louage", ...)
41:     @GetMapping("/{id}")
42:     public ResponseEntity<Louage> getOne(@PathVariable Long id) {
43:         return repository.findById(id)
44:                 .map(ResponseEntity::ok)
45:                 .orElseGet(() -> ResponseEntity.notFound().build());
46:     }
```

| Lines | Explanation |
|---|---|
| 32–33 | Swagger text. |
| 34 | `GET /louages`. |
| 35–36 | Returns the list directly; Spring serializes it with status 200 (or `[]`). |
| 39–41 | `GET /louages/{id}`. |
| 42 | `@PathVariable Long id` — converts the URL segment; non-numeric input → 400. |
| 43 | `findById` → `Optional<Louage>`. |
| 44 | present → `200 OK` with the entity. |
| 45 | empty → `404 Not Found` with no body. |

## Create

```java
48:     @Operation(summary = "Create a louage", ...)
50:     @PostMapping
51:     public ResponseEntity<Louage> create(@RequestBody Louage louage) {
52:         louage.setId(null);
53:         if (louage.getStatus() == null) {
54:             louage.setStatus(Louage.Status.AVAILABLE);
55:         }
56:         Louage saved = repository.save(louage);
57:         return ResponseEntity
58:                 .created(URI.create("/louages/" + saved.getId()))
59:                 .body(saved);
60:     }
```

| Lines | Explanation |
|---|---|
| 50–51 | `POST /louages`; `@RequestBody` → Jackson builds a `Louage` from JSON. |
| 52 | Drop any client-supplied id, guaranteeing an INSERT instead of an accidental UPDATE. |
| 53–55 | **Default status.** If JSON has no `status` (or `null`), set `AVAILABLE`. This is what keeps the `NOT NULL` column satisfied. 🔍 This is manual defaulting; a cleaner option is a field initializer `private Status status = Status.AVAILABLE;` or `@PrePersist`. |
| 56 | INSERT; the returned object is managed and has the generated id. |
| 57–59 | `201 Created` + `Location: /louages/{id}` (relative; the public path through the gateway would be `/api/louages/{id}`) + the created JSON body. |

⚠️ No duplicate-plate check, so a repeated `plateNumber` → DB unique violation → **500**.
`LouageRepository.existsByPlateNumber` exists for exactly this check but is not used.

## Update

```java
62:     @Operation(summary = "Update a louage", ...)
64:     @PutMapping("/{id}")
65:     public ResponseEntity<Louage> update(@PathVariable Long id, @RequestBody Louage input) {
66:         return repository.findById(id)
67:                 .map(existing -> {
68:                     existing.setPlateNumber(input.getPlateNumber());
69:                     existing.setCapacity(input.getCapacity());
70:                     existing.setDriverName(input.getDriverName());
71:                     if (input.getStatus() != null) {
72:                         existing.setStatus(input.getStatus());
73:                     }
74:                     return ResponseEntity.ok(repository.save(existing));
75:                 })
76:                 .orElseGet(() -> ResponseEntity.notFound().build());
77:     }
```

| Lines | Explanation |
|---|---|
| 64–65 | `PUT /louages/{id}`; two sources of data: URL id + JSON body. |
| 66 | Load the row. |
| 67 | If present, run this lambda with the managed entity. |
| 68 | Copy plate number — even if `input` has `null` → `NOT NULL` violation → 500. |
| 69 | Copy capacity (`int` → cannot be null; missing JSON field becomes 0). |
| 70 | Copy driver name (same caveat as line 68). |
| 71–73 | **Status is special**: only overwrite when the client sent one. This lets a client update "everything except status" without knowing the enum. 🔍 This is a small departure from strict PUT semantics (which should replace all fields). |
| 74 | Save (UPDATE, since `existing` has an id) and return 200 with the saved entity. |
| 75–76 | Unknown id → 404. |
| 77 | End. |

⚠️ Updating the plate number can also collide with another row's unique value → 500.
⚠️ No check that `input.getStatus()` is a valid constant — invalid enum text in JSON is
rejected by Jackson with 400 before the method runs.

## Delete

```java
79:     @Operation(summary = "Delete a louage", ...)
81:     @DeleteMapping("/{id}")
82:     public ResponseEntity<Void> delete(@PathVariable Long id) {
83:         if (!repository.existsById(id)) {
84:             return ResponseEntity.notFound().build();
85:         }
86:         repository.deleteById(id);
87:         return ResponseEntity.noContent().build();
88:     }
89: }
```

| Lines | Explanation |
|---|---|
| 81–82 | `DELETE /louages/{id}` returning no body. |
| 83–85 | Existence check → 404 for unknown id (otherwise `deleteById` would throw). |
| 86 | `delete from louages where id = ?`. |
| 87 | 204 No Content on success. |
| 88–89 | End method/class. |

🔍 Deleting a louage does **not** touch `trip-service`. Trips referencing the deleted louage
id become dangling references. Handling that would need a saga/event or an application-level
check — out of scope for this demo.

## What is missing (and would matter in a real app)

| Concern | Current behaviour |
|---|---|
| Bean validation (`@Valid`, `@NotBlank`, `@Min`) | absent → invalid data reaches the DB |
| Duplicate plate handling | 500 instead of 409 |
| Global exception handler | none → stack traces and default error JSON |
| Pagination | `findAll()` returns everything |
| Optimistic locking | absent → lost updates |
| Authorization | absent |
| API versioning | absent |

## Try it

```bat
curl.exe http://localhost:8081/louages
curl.exe -i -X POST http://localhost:8081/louages -H "Content-Type: application/json" ^
  -d "{\"plateNumber\":\"555TUN9999\",\"capacity\":9,\"driverName\":\"Karim Jaziri\"}"
curl.exe -X PUT http://localhost:8081/louages/4 -H "Content-Type: application/json" ^
  -d "{\"plateNumber\":\"555TUN9999\",\"capacity\":9,\"driverName\":\"Karim Jaziri\",\"status\":\"ON_ROAD\"}"
curl.exe -i -X DELETE http://localhost:8081/louages/4
```

(Remember: the module does not compile until the `DataSeeder` bug is fixed.)
