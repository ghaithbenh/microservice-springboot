package tn.kira.louage.tripservice.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import tn.kira.louage.tripservice.client.LouageClient;
import tn.kira.louage.tripservice.client.StationClient;
import tn.kira.louage.tripservice.dto.Louage;
import tn.kira.louage.tripservice.dto.Station;
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

    private Louage validateReferences(Trip trip) {
        Louage louage = fetchLouage(trip.getLouageId());
        fetchStation(trip.getDepartureStationId(), "Departure station");
        fetchStation(trip.getDestinationStationId(), "Destination station");
        return louage;
    }

    private Louage fetchLouage(Long id) {
        Louage louage = louageClient.getLouage(id);
        if (louage == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Louage not found: " + id);
        }
        if (isLouageUnavailable(louage) || louage.getCapacity() <= 0) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "louage-service unavailable: cannot verify louage " + id);
        }
        return louage;
    }

    private Station fetchStation(Long id, String label) {
        Station station = stationClient.getStation(id);
        if (station == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " not found: " + id);
        }
        if (isStationUnavailable(station)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "station-service unavailable: cannot verify " + label.toLowerCase() + " " + id);
        }
        return station;
    }

    private boolean isLouageUnavailable(Louage louage) {
        return "UNKNOWN".equals(louage.getStatus()) || "Unavailable".equals(louage.getPlateNumber());
    }

    private boolean isStationUnavailable(Station station) {
        return "Unavailable".equals(station.getName()) && "Unavailable".equals(station.getCity());
    }

    private void enforceSeatCapacity(Trip trip, Louage louage) {
        int capacity = louage.getCapacity();
        if (trip.getSeatsTaken() > capacity) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "seatsTaken cannot exceed louage capacity (" + capacity + ")");
        }
        if (trip.getSeatsTaken() >= capacity) {
            trip.setStatus(Trip.Status.DEPARTED);
        }
    }

    @Operation(summary = "Create a trip",
            description = "Creates a new trip referencing a louage and two stations by id, and returns it with its generated id.")
    @PostMapping
    public ResponseEntity<Trip> create(@Valid @RequestBody Trip trip) {
        trip.setId(null);
        if (trip.getStatus() == null) {
            trip.setStatus(Trip.Status.WAITING);
        }
        if (trip.getSeatsTaken() <= 0) {
            trip.setSeatsTaken(0);
        }
        Louage louage = validateReferences(trip);
        enforceSeatCapacity(trip, louage);
        Trip saved = repository.save(trip);
        fillTransientFields(saved);
        return ResponseEntity
                .created(URI.create("/trips/" + saved.getId()))
                .body(saved);
    }

    @Operation(summary = "Join a trip",
            description = "Takes one seat on the trip. Returns 404 if the trip does not exist, 400 if it has already departed "
                    + "or has no seats left, and 503 if the louage-service is unavailable (capacity unknown, so the trip cannot be joined). "
                    + "When the last seat is taken (seatsTaken reaches the louage capacity), the trip status becomes DEPARTED.")
    @PostMapping("/{id}/join")
    @Transactional
    public ResponseEntity<Object> join(@PathVariable Long id) {
        Trip trip = repository.findByIdForUpdate(id).orElse(null);
        if (trip == null) {
            return ResponseEntity.notFound().build();
        }
        if (trip.getStatus() == Trip.Status.DEPARTED) {
            return ResponseEntity.badRequest().body("Trip already departed");
        }
        Louage louage = louageClient.getLouage(trip.getLouageId());
        if (louage == null) {
            return ResponseEntity.badRequest().body("Louage not found: " + trip.getLouageId());
        }
        if (isLouageUnavailable(louage) || louage.getCapacity() <= 0) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Louage service unavailable: cannot verify capacity, trip cannot be joined");
        }
        int capacity = louage.getCapacity();
        if (trip.getSeatsTaken() >= capacity) {
            trip.setStatus(Trip.Status.DEPARTED);
            repository.save(trip);
            return ResponseEntity.badRequest().body("No seats available on this trip");
        }
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
    @Transactional
    public ResponseEntity<Trip> update(@PathVariable Long id, @Valid @RequestBody Trip input) {
        return repository.findByIdForUpdate(id)
                .map(existing -> {
                    Louage louage = validateReferences(input);
                    existing.setLouageId(input.getLouageId());
                    existing.setDepartureStationId(input.getDepartureStationId());
                    existing.setDestinationStationId(input.getDestinationStationId());
                    existing.setSeatsTaken(input.getSeatsTaken());
                    existing.setPricePerSeat(input.getPricePerSeat());
                    if (input.getStatus() != null) {
                        existing.setStatus(input.getStatus());
                    }
                    enforceSeatCapacity(existing, louage);
                    Trip saved = repository.save(existing);
                    fillTransientFields(saved);
                    return ResponseEntity.ok(saved);
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
