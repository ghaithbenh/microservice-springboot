package tn.kira.louage.tripservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class TripServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TripServiceApplication.class, args);
    }
}
// TODO: entity Trip (+ @Transient louage, departureStation, destinationStation),
//       TripRepository, TripController, Feign clients (louage-service, station-service),
//       fallback classes, DTOs Louage and Station
