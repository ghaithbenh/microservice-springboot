package tn.kira.louage.tripservice.controller;

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
import tn.kira.louage.tripservice.entity.Trip;
import tn.kira.louage.tripservice.repository.TripRepository;

@RestController
@RequestMapping("/trips")
@Tag(name = "Trips", description = "CRUD operations for louage trips")
public class TripController {

    private final TripRepository repository;

    public TripController(TripRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "List all trips",
            description = "Returns every trip in the registry.")
    @GetMapping
    public List<Trip> getAll() {
        return repository.findAll();
    }

    @Operation(summary = "Get one trip",
            description = "Returns the trip with the given id, or 404 if it does not exist.")
    @GetMapping("/{id}")
    public ResponseEntity<Trip> getOne(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Create a trip",
            description = "Creates a new trip referencing a louage and two stations by id, and returns it with its generated id.")
    @PostMapping
    public ResponseEntity<Trip> create(@RequestBody Trip trip) {
        trip.setId(null);
        if (trip.getStatus() == null) {
            trip.setStatus(Trip.Status.WAITING);
        }
        if (trip.getSeatsTaken() <= 0) {
            trip.setSeatsTaken(0);
        }
        Trip saved = repository.save(trip);
        return ResponseEntity
                .created(URI.create("/trips/" + saved.getId()))
                .body(saved);
    }

    @Operation(summary = "Update a trip",
            description = "Updates the louage, stations, seat counter, price and status of an existing trip. Returns 404 if the id is unknown.")
    @PutMapping("/{id}")
    public ResponseEntity<Trip> update(@PathVariable Long id, @RequestBody Trip input) {
        return repository.findById(id)
                .map(existing -> {
                    existing.setLouageId(input.getLouageId());
                    existing.setDepartureStationId(input.getDepartureStationId());
                    existing.setDestinationStationId(input.getDestinationStationId());
                    existing.setSeatsTaken(input.getSeatsTaken());
                    existing.setPricePerSeat(input.getPricePerSeat());
                    if (input.getStatus() != null) {
                        existing.setStatus(input.getStatus());
                    }
                    return ResponseEntity.ok(repository.save(existing));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Delete a trip",
            description = "Deletes the trip with the given id. Returns 404 if the id is unknown.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
