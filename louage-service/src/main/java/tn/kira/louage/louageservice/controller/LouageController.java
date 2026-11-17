package tn.kira.louage.louageservice.controller;

import java.net.URI;
import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import tn.kira.louage.louageservice.config.CacheConfig;
import tn.kira.louage.louageservice.entity.Louage;
import tn.kira.louage.louageservice.repository.LouageRepository;
import tn.kira.louage.louageservice.service.LouageQueryService;

@RestController
@RequestMapping("/louages")
@Tag(name = "Louages", description = "CRUD operations for louages")
public class LouageController {

    private final LouageRepository repository;
    private final LouageQueryService queryService;

    public LouageController(LouageRepository repository, LouageQueryService queryService) {
        this.repository = repository;
        this.queryService = queryService;
    }

    @Operation(summary = "List all louages",
            description = "Returns every louage in the registry.")
    @GetMapping
    public List<Louage> getAll() {
        return queryService.all();
    }

    @Operation(summary = "Get one louage",
            description = "Returns the louage with the given id, or 404 if it does not exist.")
    @GetMapping("/{id}")
    public ResponseEntity<Louage> getOne(@PathVariable Long id) {
        Louage louage = queryService.byId(id);
        return louage == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(louage);
    }

    @Operation(summary = "Create a louage",
            description = "Creates a new louage and returns it with its generated id.")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(cacheNames = CacheConfig.LOUAGES_CACHE, allEntries = true)
    public ResponseEntity<Louage> create(@Valid @RequestBody Louage louage) {
        louage.setId(null);
        if (louage.getStatus() == null) {
            louage.setStatus(Louage.Status.AVAILABLE);
        }
        Louage saved = repository.save(louage);
        return ResponseEntity
                .created(URI.create("/louages/" + saved.getId()))
                .body(saved);
    }

    @Operation(summary = "Update a louage",
            description = "Updates the plate number, capacity, driver name and status of an existing louage. Returns 404 if the id is unknown.")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(cacheNames = CacheConfig.LOUAGES_CACHE, allEntries = true)
    public ResponseEntity<Louage> update(@PathVariable Long id, @Valid @RequestBody Louage input) {
        return repository.findById(id)
                .map(existing -> {
                    existing.setPlateNumber(input.getPlateNumber());
                    existing.setCapacity(input.getCapacity());
                    existing.setDriverName(input.getDriverName());
                    if (input.getStatus() != null) {
                        existing.setStatus(input.getStatus());
                    }
                    return ResponseEntity.ok(repository.save(existing));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Delete a louage",
            description = "Deletes the louage with the given id. Returns 404 if the id is unknown.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(cacheNames = CacheConfig.LOUAGES_CACHE, allEntries = true)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
