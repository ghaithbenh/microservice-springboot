package tn.kira.louage.tripservice.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;
import tn.kira.louage.tripservice.entity.Trip;
import tn.kira.louage.tripservice.repository.TripRepository;

@Configuration
@Slf4j
public class DataSeeder {

    @Bean
    CommandLineRunner seedTrips(TripRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                log.info("Trips already seeded, skipping.");
                return;
            }
            repository.saveAll(List.of(
                    Trip.builder()
                            .louageId(1L)
                            .departureStationId(1L)   // Tunis
                            .destinationStationId(3L) // Sfax
                            .seatsTaken(4)
                            .pricePerSeat(12.5)
                            .status(Trip.Status.WAITING)
                            .build(),
                    Trip.builder()
                            .louageId(2L)
                            .departureStationId(2L)   // Sousse
                            .destinationStationId(4L) // Gabès
                            .seatsTaken(2)
                            .pricePerSeat(9.0)
                            .status(Trip.Status.WAITING)
                            .build()
            ));
            log.info("Seeded 2 trips.");
        };
    }
}
