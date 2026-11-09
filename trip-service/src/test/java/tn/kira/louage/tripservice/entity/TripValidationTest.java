package tn.kira.louage.tripservice.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class TripValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private static Trip validTrip() {
        return Trip.builder()
                .louageId(1L)
                .departureStationId(1L)
                .destinationStationId(3L)
                .seatsTaken(0)
                .pricePerSeat(12.5)
                .status(Trip.Status.WAITING)
                .build();
    }

    private static Set<String> messages(Trip trip) {
        return validator.validate(trip).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void validTripHasNoViolations() {
        assertThat(validator.validate(validTrip())).isEmpty();
    }

    @Test
    void missingLouageIdIsRejected() {
        Trip trip = validTrip();
        trip.setLouageId(null);

        assertThat(messages(trip)).contains("louageId is required");
    }

    @Test
    void missingDepartureStationIdIsRejected() {
        Trip trip = validTrip();
        trip.setDepartureStationId(null);

        assertThat(messages(trip)).contains("departureStationId is required");
    }

    @Test
    void missingDestinationStationIdIsRejected() {
        Trip trip = validTrip();
        trip.setDestinationStationId(null);

        assertThat(messages(trip)).contains("destinationStationId is required");
    }

    @Test
    void negativeSeatsTakenIsRejected() {
        Trip trip = validTrip();
        trip.setSeatsTaken(-1);

        assertThat(messages(trip)).contains("seatsTaken must be zero or positive");
    }

    @Test
    void zeroSeatsTakenIsAllowed() {
        Trip trip = validTrip();
        trip.setSeatsTaken(0);

        assertThat(validator.validate(trip)).isEmpty();
    }

    @Test
    void nonPositivePriceIsRejected() {
        Trip zero = validTrip();
        zero.setPricePerSeat(0.0);
        Trip negative = validTrip();
        negative.setPricePerSeat(-5.0);

        assertThat(messages(zero)).contains("pricePerSeat must be positive");
        assertThat(messages(negative)).contains("pricePerSeat must be positive");
    }
}
