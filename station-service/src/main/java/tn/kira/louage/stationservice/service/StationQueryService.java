package tn.kira.louage.stationservice.service;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import tn.kira.louage.stationservice.config.CacheConfig;
import tn.kira.louage.stationservice.entity.Station;
import tn.kira.louage.stationservice.repository.StationRepository;

@Service
public class StationQueryService {

    private final StationRepository repository;

    public StationQueryService(StationRepository repository) {
        this.repository = repository;
    }

    @Cacheable(cacheNames = CacheConfig.STATIONS_CACHE, key = "'all'")
    public List<Station> all() {
        return repository.findAll();
    }

    @Cacheable(cacheNames = CacheConfig.STATIONS_CACHE, key = "#id")
    public Station byId(Long id) {
        return repository.findById(id).orElse(null);
    }
}
