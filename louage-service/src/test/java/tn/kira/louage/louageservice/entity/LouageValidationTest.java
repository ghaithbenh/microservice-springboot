package tn.kira.louage.louageservice.entity;

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

class LouageValidationTest {

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

    private static Louage validLouage() {
        return Louage.builder()
                .plateNumber("123TUN4567")
                .capacity(9)
                .driverName("Ali Ben Salah")
                .status(Louage.Status.AVAILABLE)
                .build();
    }

    private static Set<String> messages(Louage louage) {
        return validator.validate(louage).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void validLouageHasNoViolations() {
        assertThat(validator.validate(validLouage())).isEmpty();
    }

    @Test
    void blankPlateNumberIsRejected() {
        Louage louage = validLouage();
        louage.setPlateNumber("   ");

        assertThat(messages(louage)).contains("plateNumber is required");
    }

    @Test
    void nullPlateNumberIsRejected() {
        Louage louage = validLouage();
        louage.setPlateNumber(null);

        assertThat(messages(louage)).contains("plateNumber is required");
    }

    @Test
    void nonPositiveCapacityIsRejected() {
        Louage zero = validLouage();
        zero.setCapacity(0);
        Louage negative = validLouage();
        negative.setCapacity(-1);

        assertThat(messages(zero)).contains("capacity must be positive");
        assertThat(messages(negative)).contains("capacity must be positive");
    }

    @Test
    void blankDriverNameIsRejected() {
        Louage louage = validLouage();
        louage.setDriverName("");

        assertThat(messages(louage)).contains("driverName is required");
    }
}
