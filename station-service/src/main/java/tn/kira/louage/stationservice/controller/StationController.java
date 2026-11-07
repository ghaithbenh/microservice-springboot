package tn.kira.louage.stationservice.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
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
import tn.kira.louage.stationservice.entity.Station;
import tn.kira.louage.stationservice.repository.StationRepository;

@RestController
@RequestMapping("/stations")
@Tag(name = "Stations", description = "CRUD operations for louage stations")
public class StationController {

    private final StationRepository repository;

    public StationController(StationRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "List all stations",
            description = "Returns every louage station in the registry.")
    @GetMapping
    public List<Station> getAll() {
        return repository.findAll();
    }

    @Operation(summary = "Get one station",
            description = "Returns the station with the given id, or 404 if it does not exist.")
    @GetMapping("/{id}")
    public ResponseEntity<Station> getOne(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Create a station",
            description = "Creates a new louage station and returns it with its generated id.")
    @PostMapping
    public ResponseEntity<Station> create(@RequestBody Station station) {
        station.setId(null);
        Station saved = repository.save(station);
        return ResponseEntity
                .created(URI.create("/stations/" + saved.getId()))
                .body(saved);
    }

    @Operation(summary = "Update a station",
            description = "Updates the name, city and governorate of an existing station. Returns 404 if the id is unknown.")
    @PutMapping("/{id}")
    public ResponseEntity<Station> update(@PathVariable Long id, @RequestBody Station input) {
        return repository.findById(id)
                .map(existing -> {
                    existing.setName(input.getName());
                    existing.setCity(input.getCity());
                    existing.setGovernorate(input.getGovernorate());
                    return ResponseEntity.ok(repository.save(existing));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Delete a station",
            description = "Deletes the station with the given id. Returns 404 if the id is unknown.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
