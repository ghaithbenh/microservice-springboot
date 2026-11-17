package tn.kira.louage.louageservice.service;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import tn.kira.louage.louageservice.config.CacheConfig;
import tn.kira.louage.louageservice.entity.Louage;
import tn.kira.louage.louageservice.repository.LouageRepository;

@Service
public class LouageQueryService {

    private final LouageRepository repository;

    public LouageQueryService(LouageRepository repository) {
        this.repository = repository;
    }

    @Cacheable(cacheNames = CacheConfig.LOUAGES_CACHE, key = "'all'")
    public List<Louage> all() {
        return repository.findAll();
    }

    @Cacheable(cacheNames = CacheConfig.LOUAGES_CACHE, key = "#id")
    public Louage byId(Long id) {
        return repository.findById(id).orElse(null);
    }
}
