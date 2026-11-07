package tn.kira.louage.stationservice.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;
import tn.kira.louage.stationservice.entity.Station;
import tn.kira.louage.stationservice.repository.StationRepository;

@Configuration
@Slf4j
public class DataSeeder {

    @Bean
    CommandLineRunner seedStations(StationRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                log.info("Stations already seeded, skipping.");
                return;
            }
            repository.saveAll(List.of(
                    Station.builder().name("Station Moncef Bey").city("Tunis").governorate("Tunis").build(),
                    Station.builder().name("Station Sousse").city("Sousse").governorate("Sousse").build(),
                    Station.builder().name("Station Melki").city("Sfax").governorate("Sfax").build(),
                    Station.builder().name("Station Gabès").city("Gabès").governorate("Gabès").build()
            ));
            log.info("Seeded 4 stations: Tunis, Sousse, Sfax, Gabès.");
        };
    }
}
