package tn.kira.louage.stationservice.controller;

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

import tn.kira.louage.stationservice.entity.Station;
import tn.kira.louage.stationservice.repository.StationRepository;

@ExtendWith(MockitoExtension.class)
class StationControllerTest {

    @Mock
    private StationRepository repository;

    @InjectMocks
    private StationController controller;

    private static Station station(Long id, String name) {
        return Station.builder()
                .id(id)
                .name(name)
                .city("City")
                .governorate("Governorate")
                .build();
    }

    private void stubSaveAssignsId(Long id) {
        when(repository.save(any(Station.class))).thenAnswer(invocation -> {
            Station saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(id);
            }
            return saved;
        });
    }

    @Test
    void getAllReturnsRepositoryContent() {
        when(repository.findAll()).thenReturn(List.of(station(1L, "Station Moncef Bey")));

        assertThat(controller.getAll()).hasSize(1);
    }

    @Test
    void getOneReturns200WhenFound() {
        when(repository.findById(1L)).thenReturn(Optional.of(station(1L, "Station Moncef Bey")));

        ResponseEntity<Station> response = controller.getOne(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("Station Moncef Bey");
    }

    @Test
    void getOneReturns404WhenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThat(controller.getOne(99L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createClearsIdAndReturns201() {
        stubSaveAssignsId(5L);
        Station input = station(99L, "New Station");

        ResponseEntity<Station> response = controller.create(input);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasToString("/stations/5");
        assertThat(response.getBody().getId()).isEqualTo(5L);
        assertThat(response.getBody().getName()).isEqualTo("New Station");
    }

    @Test
    void updateAppliesAllFields() {
        Station existing = station(1L, "Old");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        stubSaveAssignsId(1L);

        Station input = Station.builder()
                .name("New")
                .city("New City")
                .governorate("New Governorate")
                .build();

        ResponseEntity<Station> response = controller.update(1L, input);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("New");
        assertThat(response.getBody().getCity()).isEqualTo("New City");
        assertThat(response.getBody().getGovernorate()).isEqualTo("New Governorate");
    }

    @Test
    void updateReturns404WhenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThat(controller.update(99L, station(null, "New")).getStatusCode())
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
