package tn.kira.louage.tripservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import tn.kira.louage.tripservice.client.LouageClient;
import tn.kira.louage.tripservice.client.StationClient;
import tn.kira.louage.tripservice.dto.Louage;
import tn.kira.louage.tripservice.dto.Station;
import tn.kira.louage.tripservice.entity.Trip;
import tn.kira.louage.tripservice.repository.TripRepository;

@ExtendWith(MockitoExtension.class)
class TripControllerTest {

    @Mock
    private TripRepository repository;

    @Mock
    private LouageClient louageClient;

    @Mock
    private StationClient stationClient;

    @InjectMocks
    private TripController controller;

    // ---------------------------------------------------------------- helpers

    private static Trip trip(Long id, int seatsTaken, Trip.Status status) {
        return Trip.builder()
                .id(id)
                .louageId(1L)
                .departureStationId(1L)
                .destinationStationId(3L)
                .seatsTaken(seatsTaken)
                .pricePerSeat(10.0)
                .status(status)
                .build();
    }

    private static Trip newTrip(int seatsTaken, Trip.Status status) {
        return trip(null, seatsTaken, status);
    }

    private static Louage louage(int capacity) {
        return Louage.builder()
                .id(1L)
                .plateNumber("123TUN4567")
                .capacity(capacity)
                .driverName("Ali Ben Salah")
                .status("AVAILABLE")
                .build();
    }

    private static Louage unavailableLouage() {
        return Louage.builder()
                .id(1L)
                .plateNumber("Unavailable")
                .capacity(0)
                .driverName("Info temporarily unavailable")
                .status("UNKNOWN")
                .build();
    }

    private static Station station(Long id) {
        return Station.builder()
                .id(id)
                .name("Station")
                .city("City")
                .governorate("Governorate")
                .build();
    }

    private static Station unavailableStation(Long id) {
        return Station.builder()
                .id(id)
                .name("Unavailable")
                .city("Unavailable")
                .governorate("Unavailable")
                .build();
    }

    private void stubValidReferences(int capacity) {
        when(louageClient.getLouage(anyLong())).thenReturn(louage(capacity));
        when(stationClient.getStation(anyLong())).thenAnswer(invocation -> station(invocation.getArgument(0)));
    }

    private void stubSaveAssignsId() {
        when(repository.save(any(Trip.class))).thenAnswer(invocation -> {
            Trip saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(10L);
            }
            return saved;
        });
    }

    // ---------------------------------------------------------------- getAll / getOne

    @Test
    void getAllEnrichesEveryTrip() {
        when(repository.findAll()).thenReturn(List.of(trip(1L, 4, Trip.Status.WAITING)));
        when(louageClient.getLouage(1L)).thenReturn(louage(9));
        when(stationClient.getStation(1L)).thenReturn(station(1L));
        when(stationClient.getStation(3L)).thenReturn(station(3L));

        List<Trip> trips = controller.getAll();

        assertThat(trips).hasSize(1);
        assertThat(trips.get(0).getLouage().getPlateNumber()).isEqualTo("123TUN4567");
        assertThat(trips.get(0).getDepartureStation().getId()).isEqualTo(1L);
        assertThat(trips.get(0).getDestinationStation().getId()).isEqualTo(3L);
    }

    @Test
    void getOneReturns404WhenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThat(controller.getOne(99L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getOneReturnsEnrichedTrip() {
        when(repository.findById(1L)).thenReturn(Optional.of(trip(1L, 4, Trip.Status.WAITING)));
        when(louageClient.getLouage(1L)).thenReturn(louage(9));
        when(stationClient.getStation(1L)).thenReturn(station(1L));
        when(stationClient.getStation(3L)).thenReturn(station(3L));

        ResponseEntity<Trip> response = controller.getOne(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getLouage().getCapacity()).isEqualTo(9);
    }

    // ---------------------------------------------------------------- create

    @Test
    void createAppliesDefaultsAndReturns201() {
        stubValidReferences(9);
        stubSaveAssignsId();

        ResponseEntity<Trip> response = controller.create(newTrip(-3, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasToString("/trips/10");
        assertThat(response.getBody().getSeatsTaken()).isZero();
        assertThat(response.getBody().getStatus()).isEqualTo(Trip.Status.WAITING);
        assertThat(response.getBody().getLouage()).isNotNull();
        verify(repository).save(any(Trip.class));
    }

    @Test
    void createRejectsUnknownLouageWith400() {
        when(louageClient.getLouage(1L)).thenReturn(null);

        assertThatThrownBy(() -> controller.create(newTrip(0, Trip.Status.WAITING)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).contains("Louage not found: 1");
                });
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsUnknownStationWith400() {
        when(louageClient.getLouage(anyLong())).thenReturn(louage(9));
        when(stationClient.getStation(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> controller.create(newTrip(0, Trip.Status.WAITING)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).contains("Departure station not found");
                });
    }

    @Test
    void createRejectsUnavailableLouageWith503() {
        when(louageClient.getLouage(anyLong())).thenReturn(unavailableLouage());

        assertThatThrownBy(() -> controller.create(newTrip(0, Trip.Status.WAITING)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void createRejectsUnavailableStationWith503() {
        when(louageClient.getLouage(anyLong())).thenReturn(louage(9));
        when(stationClient.getStation(anyLong())).thenAnswer(invocation -> unavailableStation(invocation.getArgument(0)));

        assertThatThrownBy(() -> controller.create(newTrip(0, Trip.Status.WAITING)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void createRejectsSeatsAboveCapacityWith400() {
        stubValidReferences(9);

        assertThatThrownBy(() -> controller.create(newTrip(10, Trip.Status.WAITING)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).contains("cannot exceed louage capacity");
                });
    }

    @Test
    void createMarksTripDepartedWhenCapacityReached() {
        stubValidReferences(9);
        stubSaveAssignsId();

        ResponseEntity<Trip> response = controller.create(newTrip(9, Trip.Status.WAITING));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getStatus()).isEqualTo(Trip.Status.DEPARTED);
    }

    // ---------------------------------------------------------------- join

    @Test
    void joinReturns404WhenTripMissing() {
        when(repository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThat(controller.join(99L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void joinRejectsDepartedTripWith400() {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(trip(1L, 9, Trip.Status.DEPARTED)));

        ResponseEntity<Object> response = controller.join(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("Trip already departed");
        verify(repository, never()).save(any());
    }

    @Test
    void joinRejectsUnknownLouageWith400() {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(trip(1L, 4, Trip.Status.WAITING)));
        when(louageClient.getLouage(1L)).thenReturn(null);

        ResponseEntity<Object> response = controller.join(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("Louage not found: 1");
    }

    @Test
    void joinRefusesWhenCapacityCannotBeVerified() {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(trip(1L, 4, Trip.Status.WAITING)));
        when(louageClient.getLouage(1L)).thenReturn(unavailableLouage());

        ResponseEntity<Object> response = controller.join(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat((String) response.getBody()).contains("cannot verify capacity");
        verify(repository, never()).save(any());
    }

    @Test
    void joinRejectsFullTripAndForcesDepartedStatus() {
        Trip full = trip(1L, 9, Trip.Status.WAITING);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(full));
        when(louageClient.getLouage(1L)).thenReturn(louage(9));
        stubSaveAssignsId();

        ResponseEntity<Object> response = controller.join(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("No seats available on this trip");
        assertThat(full.getStatus()).isEqualTo(Trip.Status.DEPARTED);
        assertThat(full.getSeatsTaken()).isEqualTo(9);
        verify(repository).save(full);
    }

    @Test
    void joinIncrementsOneSeat() {
        Trip existing = trip(1L, 4, Trip.Status.WAITING);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(louageClient.getLouage(1L)).thenReturn(louage(9));
        when(stationClient.getStation(1L)).thenReturn(station(1L));
        when(stationClient.getStation(3L)).thenReturn(station(3L));
        stubSaveAssignsId();

        ResponseEntity<Object> response = controller.join(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Trip body = (Trip) response.getBody();
        assertThat(body.getSeatsTaken()).isEqualTo(5);
        assertThat(body.getStatus()).isEqualTo(Trip.Status.WAITING);
    }

    @Test
    void joinMarksDepartedWhenLastSeatTaken() {
        Trip existing = trip(1L, 8, Trip.Status.WAITING);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(louageClient.getLouage(1L)).thenReturn(louage(9));
        when(stationClient.getStation(1L)).thenReturn(station(1L));
        when(stationClient.getStation(3L)).thenReturn(station(3L));
        stubSaveAssignsId();

        ResponseEntity<Object> response = controller.join(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Trip body = (Trip) response.getBody();
        assertThat(body.getSeatsTaken()).isEqualTo(9);
        assertThat(body.getStatus()).isEqualTo(Trip.Status.DEPARTED);
    }

    @Test
    void joinNeverExceedsCapacity() {
        Trip existing = trip(1L, 8, Trip.Status.WAITING);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(louageClient.getLouage(1L)).thenReturn(louage(9));
        when(stationClient.getStation(1L)).thenReturn(station(1L));
        when(stationClient.getStation(3L)).thenReturn(station(3L));
        stubSaveAssignsId();

        controller.join(1L);
        // A second join must be refused by the DEPARTED guard.
        ResponseEntity<Object> second = controller.join(1L);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(existing.getSeatsTaken()).isEqualTo(9);
    }

    // ---------------------------------------------------------------- update

    @Test
    void updateReturns404WhenTripMissing() {
        when(repository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThat(controller.update(99L, newTrip(0, Trip.Status.WAITING)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateAppliesChanges() {
        Trip existing = trip(1L, 4, Trip.Status.WAITING);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        stubValidReferences(9);
        stubSaveAssignsId();

        Trip input = Trip.builder()
                .louageId(1L)
                .departureStationId(1L)
                .destinationStationId(3L)
                .seatsTaken(6)
                .pricePerSeat(20.0)
                .status(Trip.Status.WAITING)
                .build();

        ResponseEntity<Trip> response = controller.update(1L, input);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getSeatsTaken()).isEqualTo(6);
        assertThat(response.getBody().getPricePerSeat()).isEqualTo(20.0);
    }

    @Test
    void updateRejectsSeatsAboveCapacityWith400() {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(trip(1L, 4, Trip.Status.WAITING)));
        stubValidReferences(9);

        assertThatThrownBy(() -> controller.update(1L, newTrip(10, Trip.Status.WAITING)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(repository, never()).save(any());
    }

    @Test
    void updateMarksDepartedWhenCapacityReached() {
        Trip existing = trip(1L, 4, Trip.Status.WAITING);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        stubValidReferences(9);
        stubSaveAssignsId();

        ResponseEntity<Trip> response = controller.update(1L, newTrip(9, Trip.Status.WAITING));

        assertThat(response.getBody().getStatus()).isEqualTo(Trip.Status.DEPARTED);
    }

    // ---------------------------------------------------------------- delete

    @Test
    void deleteReturns404WhenMissing() {
        when(repository.existsById(99L)).thenReturn(false);

        assertThat(controller.delete(99L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(repository, never()).deleteById(anyLong());
    }

    @Test
    void deleteReturns204WhenPresent() {
        when(repository.existsById(1L)).thenReturn(true);

        assertThat(controller.delete(1L).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(repository).deleteById(1L);
    }
}
