# TripRepository

> File: `trip-service/src/main/java/tn/kira/louage/tripservice/repository/TripRepository.java`
> Package: `tn.kira.louage.tripservice.repository`
> Type: Spring Data JPA interface (no custom methods)

## The code

```java
1: package tn.kira.louage.tripservice.repository;
2:
3: import org.springframework.data.jpa.repository.JpaRepository;
4: import tn.kira.louage.tripservice.entity.Trip;
5:
6: public interface TripRepository extends JpaRepository<Trip, Long> {
7: }
```

## Line by line

| Lines | Explanation |
|---|---|
| 1 | Repository package. |
| 3 | Base interface providing CRUD. |
| 4 | Entity managed here. |
| 6 | `<Trip, Long>` = entity type and primary-key type. `Long` must match `Trip.id`. |
| 7 | Empty body: only the inherited API is used. |

## Inherited methods used by `TripController`

| Method | Call site |
|---|---|
| `findAll()` | `getAll()` |
| `findById(Long)` | `getOne()`, `update()` |
| `save(Trip)` | `create()`, `update()` |
| `existsById(Long)` | `delete()` |
| `deleteById(Long)` | `delete()` |
| `count()` | `DataSeeder` guard |
| `saveAll(Iterable)` | `DataSeeder` |

## Why there are no query methods here

The controller only needs CRUD by id. The interesting queries for an orchestrator would be
business-oriented, e.g.:

```java
List<Trip> findByStatus(Trip.Status status);                      // waiting trips
List<Trip> findByLouageId(Long louageId);                         // trips of one vehicle
List<Trip> findByDepartureStationIdAndDestinationStationId(Long a, Long b);
long countByLouageIdAndStatus(Long louageId, Trip.Status status);
boolean existsByLouageIdAndStatus(Long louageId, Trip.Status status);
```

None are needed for the current endpoints, so the interface stays empty.

## Reminder about the proxy

Spring Data generates the implementation at startup (`SimpleJpaRepository` backed by an
`EntityManager`) and registers it as the `tripRepository` bean. Constructor injection in
`TripController` receives it. No implementation file exists or is needed.

## Note for the Feign/orchestration TODO

When `trip-service` starts calling `louage-service` and `station-service`, the repository
stays exactly like this — orchestration lives in a service/controller layer, not in the
repository. The repository is only responsible for the local `trips` table.
