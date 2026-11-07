package tn.kira.louage.stationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class StationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(StationServiceApplication.class, args);
    }
}
// TODO: entity Station, StationRepository, StationController (CRUD under /stations)
