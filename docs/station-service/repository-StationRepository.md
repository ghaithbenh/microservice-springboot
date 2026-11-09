# StationRepository

> File: `station-service/src/main/java/tn/kira/louage/stationservice/repository/StationRepository.java`
> Package: `tn.kira.louage.stationservice.repository`
> Type: Spring Data JPA repository **interface** (no implementation class)

## The code

```java
1: package tn.kira.louage.stationservice.repository;
2:
3: import org.springframework.data.jpa.repository.JpaRepository;
4: import tn.kira.louage.stationservice.entity.Station;
5:
6: public interface StationRepository extends JpaRepository<Station, Long> {
7: }
```

## Line by line

| Lines | Explanation |
|---|---|
| 1 | Repository layer package. |
| 3 | `JpaRepository` is the interface we extend. It is part of Spring Data JPA. |
| 4 | The entity managed by this repository. |
| 6 | `interface` + `extends JpaRepository<Station, Long>`. The two generics are `<EntityType, PrimaryKeyType>`. **There is no body** — no `@Repository`, no `@Component`, no SQL. |
| 7 | Empty body. Nothing to add: every CRUD method is inherited. |

## How an interface with no implementation works

At startup Spring Data scans for interfaces extending `Repository` (this is enabled by
`spring-boot-starter-data-jpa` auto-configuration). For each one it creates a
**dynamic proxy** implementing the interface with:

- a JPA `EntityManager` to talk to Hibernate;
- an implementation of every method declared in `JpaRepository` and its parents;
- query methods generated from names (none here).

The proxy is registered as a bean named `stationRepository` (decapitalised interface name).
`StationController`'s constructor asks for `StationRepository` and gets this proxy.
You never write `new StationRepositoryImpl()`.

## Inherited API (what you can call)

| Method | SQL equivalent | Returns |
|---|---|---|
| `save(Station s)` | `insert` when id is null, else `update` | the managed entity (with id) |
| `saveAll(Iterable<Station>)` | batch of inserts/updates | `List<Station>` |
| `findById(Long id)` | `select ... where id = ?` | `Optional<Station>` |
| `findAll()` | `select ...` | `List<Station>` |
| `findAllById(Iterable<Long>)` | `select ... where id in (...)` | `List<Station>` |
| `existsById(Long id)` | `select count(*) > 0 ...` | `boolean` |
| `count()` | `select count(*)` | `long` |
| `deleteById(Long id)` | `delete ... where id = ?` | `void` (throws `EmptyResultDataAccessException` if absent) |
| `delete(Station s)` | `delete` that row | `void` |
| `deleteAll()` | `delete` everything | `void` |
| `flush()` | force pending SQL now | `void` |

It also inherits `PagingAndSortingRepository` (`findAll(Pageable)`,
`findAll(Sort)`) and `CrudRepository` (the methods above are declared there).

🔍 No implementation, no SQL, no annotations on our side — but all of this exists at runtime.

## Why the repository is separate from the controller

- The controller only knows *HTTP*; the repository only knows *data*.
- You can swap the database (H2 → PostgreSQL) by changing `application.yml`; no Java change.
- You can add caching or auditing in one place.
- Tests can mock the repository and test the controller without a database.

## Where to add queries

If you need `findByName(String name)`, just declare it:

```java
List<Station> findByName(String name);          // where name = ?
List<Station> findByCityAndGovernorate(String city, String gov);
boolean existsByNameIgnoreCase(String name);
```

Spring Data parses the method name according to the grammar
`findBy` / `existsBy` / `countBy` / `deleteBy` + property + operators (`And`, `Or`, `IgnoreCase`,
`GreaterThan`, `Containing`, `OrderBy...`). It validates the property names **at startup** —
a typo such as `findByNaam` fails the context with "No property 'naam' found for type Station".

For complex SQL use `@Query`:

```java
@Query("select s from Station s where lower(s.name) like lower(concat('%', :q, '%'))")
List<Station> search(@Param("q") String q);
```

## Notes specific to this project

- **No custom methods** — the project only needs the CRUD inherited from `JpaRepository`.
- `existsById` is used by the controller's delete because `deleteById` throws when the row
  is missing.
- `count()` is used by `DataSeeder` to decide whether seeding is necessary.
