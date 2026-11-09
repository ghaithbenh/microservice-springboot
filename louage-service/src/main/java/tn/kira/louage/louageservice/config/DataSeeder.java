package tn.kira.louage.louageservice.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;
import tn.kira.louage.louageservice.entity.Louage;
import tn.kira.louage.louageservice.repository.LouageRepository;

@Configuration
@Slf4j
public class DataSeeder {

    @Bean
    CommandLineRunner seedLouages(LouageRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                log.info("Louages already seeded, skipping.");
                return;
            }
            repository.saveAll(List.of(
                    Louage.builder().plateNumber("123TUN4567").capacity(9).driverName("Ali Ben Salah").status(Louage.Status.AVAILABLE).build(),
                    Louage.builder().plateNumber("987TUN3210").capacity(8).driverName("Mehdi Trabelsi").status(Louage.Status.AVAILABLE).build(),
                    Louage.builder().plateNumber("145TUN7890").capacity(9).driverName("Sami Gharbi").status(Louage.Status.ON_ROAD).build()
            ));
            log.info("Seeded 3 louages.");
        };
    }
}
