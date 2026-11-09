package tn.kira.louage.tripservice.client.fallback;

import org.springframework.stereotype.Component;

import tn.kira.louage.tripservice.client.StationClient;
import tn.kira.louage.tripservice.dto.Station;

@Component
public class StationClientFallback implements StationClient {

    @Override
    public Station getStation(Long id) {
        return Station.builder()
                .id(id)
                .name("Unavailable")
                .city("Unavailable")
                .governorate("Unavailable")
                .build();
    }
}
