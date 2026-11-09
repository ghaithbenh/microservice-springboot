package tn.kira.louage.tripservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import tn.kira.louage.tripservice.client.fallback.StationClientFallback;
import tn.kira.louage.tripservice.dto.Station;

@FeignClient(name = "station-service", fallback = StationClientFallback.class, dismiss404 = true)
public interface StationClient {

    @GetMapping("/stations/{id}")
    Station getStation(@PathVariable("id") Long id);
}
