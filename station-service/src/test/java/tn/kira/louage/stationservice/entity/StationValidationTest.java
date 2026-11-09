package tn.kira.louage.stationservice.entity;

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

class StationValidationTest {

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

    private static Station validStation() {
        return Station.builder()
                .name("Station Moncef Bey")
                .city("Tunis")
                .governorate("Tunis")
                .build();
    }

    private static Set<String> messages(Station station) {
        return validator.validate(station).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void validStationHasNoViolations() {
        assertThat(validator.validate(validStation())).isEmpty();
    }

    @Test
    void blankNameIsRejected() {
        Station station = validStation();
        station.setName(" ");

        assertThat(messages(station)).contains("name is required");
    }

    @Test
    void blankCityIsRejected() {
        Station station = validStation();
        station.setCity("");

        assertThat(messages(station)).contains("city is required");
    }

    @Test
    void blankGovernorateIsRejected() {
        Station station = validStation();
        station.setGovernorate(null);

        assertThat(messages(station)).contains("governorate is required");
    }
}
