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
import tn.kira.louage.tripservice.client.LouageClient;
import tn.kira.louage.tripservice.client.StationClient;
import tn.kira.louage.tripservice.entity.Trip;
import tn.kira.louage.tripservice.repository.TripRepository;

@RestController
@RequestMapping("/trips")
@Tag(name = "Trips", description = "CRUD operations for louage trips")
public class TripController {

    private final TripRepository repository;
    private final LouageClient louageClient;
    private final StationClient stationClient;

    public TripController(TripRepository repository, LouageClient louageClient, StationClient stationClient) {
        this.repository = repository;
        this.louageClient = louageClient;
        this.stationClient = stationClient;
    }

    @Operation(summary = "List all trips",
            description = "Returns every trip in the registry.")
    @GetMapping
    public List<Trip> getAll() {
        List<Trip> trips = repository.findAll();
        trips.forEach(this::fillTransientFields);
        return trips;
    }

    @Operation(summary = "Get one trip",
            description = "Returns the trip with the given id, or 404 if it does not exist.")
    @GetMapping("/{id}")
    public ResponseEntity<Trip> getOne(@PathVariable Long id) {
        return repository.findById(id)
                .map(trip -> {
                    fillTransientFields(trip);
                    return ResponseEntity.ok(trip);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private void fillTransientFields(Trip trip) {
        trip.setLouage(louageClient.getLouage(trip.getLouageId()));
        trip.setDepartureStation(stationClient.getStation(trip.getDepartureStationId()));
        trip.setDestinationStation(stationClient.getStation(trip.getDestinationStationId()));
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

    @Operation(summary = "Join a trip",
            description = "Takes one seat on the trip. Returns 404 if the trip does not exist and 400 if it has already departed. "
                    + "When the last seat is taken (seatsTaken reaches the louage capacity), the trip status becomes DEPARTED.")
    @PostMapping("/{id}/join")
    public ResponseEntity<Object> join(@PathVariable Long id) {
        Trip trip = repository.findById(id).orElse(null);
        if (trip == null) {
            return ResponseEntity.notFound().build();
        }
        if (trip.getStatus() == Trip.Status.DEPARTED) {
            return ResponseEntity.badRequest().body("Trip already departed");
        }
        int capacity = louageClient.getLouage(trip.getLouageId()).getCapacity();
        trip.setSeatsTaken(trip.getSeatsTaken() + 1);
        if (trip.getSeatsTaken() >= capacity) {
            trip.setStatus(Trip.Status.DEPARTED);
        }
        Trip saved = repository.save(trip);
        fillTransientFields(saved);
        return ResponseEntity.ok(saved);
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
