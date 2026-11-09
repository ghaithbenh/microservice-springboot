package tn.kira.louage.louageservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

import tn.kira.louage.louageservice.entity.Louage;
import tn.kira.louage.louageservice.repository.LouageRepository;

@ExtendWith(MockitoExtension.class)
class LouageControllerTest {

    @Mock
    private LouageRepository repository;

    @InjectMocks
    private LouageController controller;

    private static Louage louage(Long id, String plate, int capacity, Louage.Status status) {
        return Louage.builder()
                .id(id)
                .plateNumber(plate)
                .capacity(capacity)
                .driverName("Driver")
                .status(status)
                .build();
    }

    private void stubSaveAssignsId(Long id) {
        when(repository.save(any(Louage.class))).thenAnswer(invocation -> {
            Louage saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(id);
            }
            return saved;
        });
    }

    @Test
    void getAllReturnsRepositoryContent() {
        when(repository.findAll()).thenReturn(List.of(louage(1L, "123TUN4567", 9, Louage.Status.AVAILABLE)));

        assertThat(controller.getAll()).hasSize(1);
    }

    @Test
    void getOneReturns200WhenFound() {
        when(repository.findById(1L)).thenReturn(Optional.of(louage(1L, "123TUN4567", 9, Louage.Status.AVAILABLE)));

        ResponseEntity<Louage> response = controller.getOne(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getPlateNumber()).isEqualTo("123TUN4567");
    }

    @Test
    void getOneReturns404WhenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThat(controller.getOne(99L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createClearsIdAndDefaultsStatus() {
        stubSaveAssignsId(5L);
        Louage input = louage(99L, "NEW-PLATE", 7, null);

        ResponseEntity<Louage> response = controller.create(input);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasToString("/louages/5");
        assertThat(response.getBody().getId()).isEqualTo(5L);
        assertThat(response.getBody().getStatus()).isEqualTo(Louage.Status.AVAILABLE);
    }

    @Test
    void createKeepsProvidedStatus() {
        stubSaveAssignsId(6L);
        Louage input = louage(null, "NEW-PLATE", 7, Louage.Status.ON_ROAD);

        ResponseEntity<Louage> response = controller.create(input);

        assertThat(response.getBody().getStatus()).isEqualTo(Louage.Status.ON_ROAD);
    }

    @Test
    void updateAppliesAllFields() {
        Louage existing = louage(1L, "OLD", 9, Louage.Status.AVAILABLE);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        stubSaveAssignsId(1L);

        ResponseEntity<Louage> response = controller.update(1L,
                louage(null, "NEW", 8, Louage.Status.ON_ROAD));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getPlateNumber()).isEqualTo("NEW");
        assertThat(response.getBody().getCapacity()).isEqualTo(8);
        assertThat(response.getBody().getStatus()).isEqualTo(Louage.Status.ON_ROAD);
    }

    @Test
    void updateReturns404WhenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThat(controller.update(99L, louage(null, "NEW", 8, null)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(repository, never()).save(any());
    }

    @Test
    void deleteReturns204WhenPresent() {
        when(repository.existsById(1L)).thenReturn(true);

        assertThat(controller.delete(1L).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(repository).deleteById(1L);
    }

    @Test
    void deleteReturns404WhenMissing() {
        when(repository.existsById(99L)).thenReturn(false);

        assertThat(controller.delete(99L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(repository, never()).deleteById(any());
    }
}
