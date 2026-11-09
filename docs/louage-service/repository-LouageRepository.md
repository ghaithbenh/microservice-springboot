# LouageRepository

> File: `louage-service/src/main/java/tn/kira/louage/louageservice/repository/LouageRepository.java`
> Package: `tn.kira.louage.louageservice.repository`
> Type: Spring Data JPA repository interface (+ one derived query)

## The code

```java
1: package tn.kira.louage.louageservice.repository;
2:
3: import org.springframework.data.jpa.repository.JpaRepository;
4: import tn.kira.louage.louageservice.entity.Louage;
5:
6: public interface LouageRepository extends JpaRepository<Louage, Long> {
7:
8:     boolean existsByPlateNumber(String plateNumber);
9: }
```

## Line by line

| Lines | Explanation |
|---|---|
| 1 | Repository package. |
| 3 | `JpaRepository` — base interface with the full CRUD API. |
| 4 | The entity type. |
| 6 | `<Louage, Long>` = entity + primary-key type. |
| 7 | Blank line. |
| 8 | **Derived query method.** Spring Data parses the name: `existsBy` + property `PlateNumber` → `boolean` result, `where plate_number = ?`. Generated SQL ≈ `select count(*) > 0 from louages l where l.plate_number = ?`. |
| 9 | Closing brace. |

## What Spring Data generates for line 8

```sql
select
    count(*) > 0
from
    louages l1_0
where
    l1_0.plate_number = ?
```

Method name grammar:

```
existsBy  PlateNumber
   │            │
   │            └── entity property (must exist; checked at startup)
   └── keyword: returns boolean
```

Possible variants you could add:

```java
Optional<Louage> findByPlateNumber(String plateNumber);
boolean existsByPlateNumberIgnoreCase(String plateNumber);
List<Louage> findByStatus(Louage.Status status);
List<Louage> findByCapacityGreaterThanEqual(int capacity);
long countByStatus(Louage.Status status);
```

## ⚠️ This method is never called

Searching the codebase, `existsByPlateNumber` has no caller. The controller's `create`
simply calls `save()`, so a duplicate plate number reaches the database and violates the
unique constraint → Hibernate throws `DataIntegrityViolationException` → Spring returns
**500 Internal Server Error** with a stack trace in the logs.

A cleaner `create` would be:

```java
@PostMapping
public ResponseEntity<Louage> create(@RequestBody Louage louage) {
    if (repository.existsByPlateNumber(louage.getPlateNumber())) {
        return ResponseEntity.status(HttpStatus.CONFLICT).build(); // 409
    }
    ...
}
```

There is still a race window between the check and the insert (two concurrent requests), so
production code would *also* keep handling the constraint violation (e.g. via a
`@RestControllerAdvice` that maps it to 409). The `existsBy...` pre-check mainly buys you a
friendlier error message and an early exit.

⚠️ If `plateNumber` is `null`, the derived query does
`where plate_number = null`, which never matches — but the insert would fail on
`NOT NULL` anyway.

## Reminder: where the proxy comes from

Spring Data creates the implementation at startup and registers it as a bean named
`louageRepository`. `LouageController` receives it through its constructor. There is no
`LouageRepositoryImpl` file in the project — that is expected.

## Differences from `StationRepository`

| | `StationRepository` | `LouageRepository` |
|---|---|---|
| Extra methods | none | `existsByPlateNumber` (declared, unused) |
| Unique business key | none | `plateNumber` |
| Everything else | identical `JpaRepository` API | identical `JpaRepository` API |
