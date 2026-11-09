package tn.kira.louage.tripservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void validationErrorsBecome400WithFieldMessages() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "trip");
        binding.addError(new FieldError("trip", "louageId", "louageId is required"));
        binding.addError(new FieldError("trip", "pricePerSeat", "pricePerSeat must be positive"));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(binding);

        ProblemDetail detail = handler.handleValidation(ex);

        assertThat(detail.getStatus()).isEqualTo(400);
        assertThat(detail.getDetail())
                .contains("louageId: louageId is required")
                .contains("pricePerSeat: pricePerSeat must be positive");
    }

    @Test
    void responseStatusExceptionKeepsStatusAndReason() {
        ProblemDetail detail = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "louage-service down"));

        assertThat(detail.getStatus()).isEqualTo(503);
        assertThat(detail.getDetail()).isEqualTo("louage-service down");
    }
}
