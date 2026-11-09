# API Reference

All three business services expose the same CRUD shape. You can call them **directly**
(`:8081`, `:8082`, `:8083`) or **through the gateway** (`:8080/api/...`).

Through the gateway, the `/api` prefix is stripped by the `RewritePath` filter:
`GET /api/louages/1` on `:8080` → `GET /louages/1` on `louage-service`.

## louage-service — `/louages` (port 8081)

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| GET | `/louages` | — | `200` list (empty list if none) | — |
| GET | `/louages/{id}` | — | `200` + louage | `404` if id unknown |
| POST | `/louages` | louage JSON | `201` + created louage, `Location: /louages/{id}` | `500` on duplicate plate |
| PUT | `/louages/{id}` | louage JSON | `200` + updated louage | `404` if id unknown |
| DELETE | `/louages/{id}` | — | `204` no body | `404` if id unknown |

Louage body:

```json
{
  "plateNumber": "123TUN4567",
  "capacity": 9,
  "driverName": "Ali Ben Salah",
  "status": "AVAILABLE"
}
```

- `status` is optional on create → defaults to `AVAILABLE`. Allowed: `AVAILABLE`, `ON_ROAD`.
- `id` is ignored on create (the controller forces `null`).
- `plateNumber` is unique in the database.

## station-service — `/stations` (port 8082)

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| GET | `/stations` | — | `200` list | — |
| GET | `/stations/{id}` | — | `200` + station | `404` |
| POST | `/stations` | station JSON | `201` + created station | `400` malformed JSON |
| PUT | `/stations/{id}` | station JSON | `200` + updated station | `404` |
| DELETE | `/stations/{id}` | — | `204` | `404` |

Station body:

```json
{
  "name": "Station Moncef Bey",
  "city": "Tunis",
  "governorate": "Tunis"
}
```

## trip-service — `/trips` (port 8083)

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| GET | `/trips` | — | `200` list | — |
| GET | `/trips/{id}` | — | `200` + trip | `404` |
| POST | `/trips` | trip JSON | `201` + created trip | `500` if non-null field missing |
| PUT | `/trips/{id}` | trip JSON | `200` + updated trip | `404` |
| DELETE | `/trips/{id}` | — | `204` | `404` |

Trip body:

```json
{
  "louageId": 1,
  "departureStationId": 1,
  "destinationStationId": 3,
  "seatsTaken": 4,
  "pricePerSeat": 12.5,
  "status": "WAITING"
}
```

- `status` optional on create → defaults to `WAITING`. Allowed: `WAITING`, `DEPARTED`.
- `seatsTaken` ≤ 0 is silently forced to `0` on create.
- The three ids are **not validated** against louage-service/station-service yet.

## Example session (PowerShell / cmd)

Use `curl.exe` on Windows (plain `curl` is an alias for `Invoke-WebRequest` in older
PowerShell versions and prints objects instead of JSON).

```bat
:: create a louage through the gateway
curl.exe -i -X POST http://localhost:8080/api/louages ^
  -H "Content-Type: application/json" ^
  -d "{\"plateNumber\":\"111TUN2222\",\"capacity\":8,\"driverName\":\"Nizar Ayari\"}"

:: list
curl.exe http://localhost:8080/api/louages

:: fetch one
curl.exe http://localhost:8080/api/louages/1

:: update (full update)
curl.exe -X PUT http://localhost:8080/api/louages/1 ^
  -H "Content-Type: application/json" ^
  -d "{\"plateNumber\":\"111TUN2222\",\"capacity\":8,\"driverName\":\"Nizar Ayari\",\"status\":\"ON_ROAD\"}"

:: delete
curl.exe -i -X DELETE http://localhost:8080/api/louages/1
```

In bash/zsh the same calls use `\` line continuations and single quotes around JSON.

## Infrastructure URLs

| URL | What |
|---|---|
| http://localhost:8761 | Eureka dashboard (registered instances) |
| http://localhost:8080/api/louages | louage-service via gateway |
| http://localhost:8080/api/stations | station-service via gateway |
| http://localhost:8080/api/trips | trip-service via gateway |
| http://localhost:8081/swagger-ui.html | Louage Swagger UI |
| http://localhost:8082/swagger-ui.html | Station Swagger UI |
| http://localhost:8083/swagger-ui.html | Trip Swagger UI |
| http://localhost:8081/v3/api-docs | Louage OpenAPI JSON |
| http://localhost:8081/h2-console | Louage DB console (`sa`, empty password) |
| http://localhost:8082/h2-console | Station DB console |
| http://localhost:8083/h2-console | Trip DB console |

## HTTP status codes you will see

| Code | Meaning in this project |
|---|---|
| 200 | Read/update succeeded |
| 201 | Entity created; `Location` header points at it |
| 204 | Delete succeeded, no body |
| 400 | Body was not valid JSON or could not be mapped to the entity |
| 404 | `findById` returned empty (we build it manually with `ResponseEntity.notFound()`) |
| 500 | Uncaught database error — e.g. duplicate `plateNumber`, or a `null` in a `nullable = false` column |
| 503 | Gateway could not find an instance of the service in Eureka (service down / not registered yet) |

## Response examples

```json
// GET /louages/1  → 200
{
  "id": 1,
  "plateNumber": "123TUN4567",
  "capacity": 9,
  "driverName": "Ali Ben Salah",
  "status": "AVAILABLE"
}
```

```json
// DELETE /louages/1  → 204, empty body
```

```json
// GET /louages/999  → 404, empty body
```
