# TripController

> File: `trip-service/src/main/java/tn/kira/louage/tripservice/controller/TripController.java`
> Package: `tn.kira.louage.tripservice.controller`
> Type: REST controller — HTTP layer for `/trips`

## Endpoints

| Method | Path | Handler | Success | Defaults applied |
|---|---|---|---|---|
| GET | `/trips` | `getAll` | 200 | — |
| GET | `/trips/{id}` | `getOne` | 200 / 404 | — |
| POST | `/trips` | `create` | 201 | `status=WAITING`, `seatsTaken≥0` |
| PUT | `/trips/{id}` | `update` | 200 / 404 | status only if non-null |
| DELETE | `/trips/{id}` | `delete` | 204 / 404 | — |

## Class declaration and injection

```java
21: @RestController
22: @RequestMapping("/trips")
23: @Tag(name = "Trips", description = "CRUD operations for louage trips")
24: public class TripController {
25:
26:     private final TripRepository repository;
27:
28:     public TripController(TripRepository repository) {
29:         this.repository = repository;
30:     }
```

| Lines | Explanation |
|---|---|
| 3–4 | `URI` and `List`. |
| 6–14 | Spring MVC annotations. |
| 16–17 | springdoc annotations (documentation only). |
| 18–19 | Entity + repository. |
| 21 | `@RestController` — bean + JSON. |
| 22 | Base path `/trips` (public: `/api/trips` via the gateway). |
| 23 | Swagger tag. |
| 24 | Class declaration. |
| 26 | Final dependency → constructor injection. |
| 28–30 | Spring supplies the `tripRepository` proxy. |

## Read endpoints

```java
32:     @Operation(summary = "List all trips", ...)
34:     @GetMapping
35:     public List<Trip> getAll() {
36:         return repository.findAll();
37:     }
39:     @Operation(summary = "Get one trip", ...)
41:     @GetMapping("/{id}")
42:     public ResponseEntity<Trip> getOne(@PathVariable Long id) {
43:         return repository.findById(id)
44:                 .map(ResponseEntity::ok)
45:                 .orElseGet(() -> ResponseEntity.notFound().build());
46:     }
```

| Lines | Explanation |
|---|---|
| 32–34 | `GET /trips`. |
| 35–36 | Returns all rows → 200 + JSON array. |
| 39–42 | `GET /trips/{id}`, id converted to `Long`. |
| 43–45 | `Optional<Trip>`: present → 200, empty → 404. |

🔍 This is the same pattern as the other services. In the intended design these responses
would also include `@Transient` louage/station details fetched via Feign; today the JSON
contains only ids.

## Create

```java
48:     @Operation(summary = "Create a trip", ...)
50:     @PostMapping
51:     public ResponseEntity<Trip> create(@RequestBody Trip trip) {
52:         trip.setId(null);
53:         if (trip.getStatus() == null) {
54:             trip.setStatus(Trip.Status.WAITING);
55:         }
56:         if (trip.getSeatsTaken() <= 0) {
57:             trip.setSeatsTaken(0);
58:         }
59:         Trip saved = repository.save(trip);
60:         return ResponseEntity
61:                 .created(URI.create("/trips/" + saved.getId()))
62:                 .body(saved);
63:     }
```

| Lines | Explanation |
|---|---|
| 50–51 | `POST /trips`; body deserialized into `Trip`. |
| 52 | Force `id = null` → always INSERT (prevents POSTing an existing id to overwrite it). |
| 53–55 | Default status to `WAITING` when the client omitted it. |
| 56–58 | `seatsTaken` is a primitive `int`; a missing field arrives as `0`, a negative value is clamped to `0`. So `-5` becomes `0` instead of an error. |
| 59 | INSERT. |
| 60–62 | `201 Created` + `Location: /trips/{id}` + created body. |

### What `create` does **not** check

| Missing check | Consequence |
|---|---|
| Does `louageId` exist in louage-service? | No — any number is accepted |
| Do the station ids exist? | No |
| Is `departureStationId != destinationStationId`? | No |
| Is `pricePerSeat >= 0`? | No |
| Is `seatsTaken <= louage.capacity`? | No; the capacity is not even known here |
| Is the louage `AVAILABLE`? | No |

These are the checks the orchestrator is supposed to add via Feign. Example of the intended
flow (not implemented):

```java
LouageDto louage = louageClient.getLouage(trip.getLouageId());        // may hit fallback
StationDto from  = stationClient.getStation(trip.getDepartureStationId());
StationDto to    = stationClient.getStation(trip.getDestinationStationId());
```

## Update

```java
65:     @Operation(summary = "Update a trip", ...)
67:     @PutMapping("/{id}")
68:     public ResponseEntity<Trip> update(@PathVariable Long id, @RequestBody Trip input) {
69:         return repository.findById(id)
70:                 .map(existing -> {
71:                     existing.setLouageId(input.getLouageId());
72:                     existing.setDepartureStationId(input.getDepartureStationId());
73:                     existing.setDestinationStationId(input.getDestinationStationId());
74:                     existing.setSeatsTaken(input.getSeatsTaken());
75:                     existing.setPricePerSeat(input.getPricePerSeat());
76:                     if (input.getStatus() != null) {
77:                         existing.setStatus(input.getStatus());
78:                     }
79:                     return ResponseEntity.ok(repository.save(existing));
80:                 })
81:                 .orElseGet(() -> ResponseEntity.notFound().build());
82:     }
```

| Lines | Explanation |
|---|---|
| 67–68 | `PUT /trips/{id}` with URL id + body. |
| 69 | Load the row. |
| 70–79 | If present: copy every mutable field from `input` onto the managed entity. |
| 71–73 | The three references are simply overwritten. ⚠️ No existence validation; the body's id fields are trusted. |
| 74 | `seatsTaken` overwritten — no clamping here (unlike `create`), so a negative value **can** be stored via PUT. ⚠️ Slight inconsistency between POST and PUT. |
| 75 | Price overwritten, no validation (negative allowed). |
| 76–78 | Status only overwritten when non-null — same pattern as the other controllers. |
| 79 | `save` → UPDATE (entity has an id) → 200 + updated body. |
| 80–82 | 404 when the id is unknown. |

### “Booking” does not exist yet

There is no `POST /trips/{id}/book` endpoint. To increase the seat counter today a client
must PUT the **entire** trip with the new `seatsTaken` — and nothing stops them from setting
`seatsTaken = 999`. The intended rule is:

```java
// pseudo-code that is NOT in the project
existing.setSeatsTaken(existing.getSeatsTaken() + 1);
if (existing.getSeatsTaken() >= louage.getCapacity()) {
    existing.setStatus(Trip.Status.DEPARTED);
}
```

## Delete

```java
84:     @Operation(summary = "Delete a trip", ...)
86:     @DeleteMapping("/{id}")
87:     public ResponseEntity<Void> delete(@PathVariable Long id) {
88:         if (!repository.existsById(id)) {
89:             return ResponseEntity.notFound().build();
90:         }
91:         repository.deleteById(id);
92:         return ResponseEntity.noContent().build();
93:     }
94: }
```

| Lines | Explanation |
|---|---|
| 86–87 | `DELETE /trips/{id}`, no response body. |
| 88–90 | Unknown id → 404 (avoids `EmptyResultDataAccessException` from `deleteById`). |
| 91 | DELETE the row. |
| 92 | 204 No Content. |
| 93–94 | End. |

## HTTP behaviour and gaps

| Situation | Actual result |
|---|---|
| Unknown id on GET/PUT/DELETE | 404 (handled manually) |
| Missing required id in POST body | `NOT NULL` violation → 500 |
| Negative `seatsTaken` on POST | clamped to 0 |
| Negative `seatsTaken` on PUT | stored as-is |
| Invalid enum value | 400 (Jackson rejects it) |
| Service can't be reached by client | 503 at the gateway; trip-service itself unaffected |
| Louage/station deleted elsewhere | trip keeps a dangling id, no error |

## Try it

```bat
curl.exe http://localhost:8083/trips
curl.exe -i -X POST http://localhost:8083/trips -H "Content-Type: application/json" ^
  -d "{\"louageId\":1,\"departureStationId\":1,\"destinationStationId\":3,\"seatsTaken\":2,\"pricePerSeat\":12.5}"
curl.exe -X PUT http://localhost:8083/trips/3 -H "Content-Type: application/json" ^
  -d "{\"louageId\":1,\"departureStationId\":1,\"destinationStationId\":3,\"seatsTaken\":9,\"pricePerSeat\":12.5,\"status\":\"DEPARTED\"}"
curl.exe -i -X DELETE http://localhost:8083/trips/3
```

Through the gateway: `http://localhost:8080/api/trips/...`.
