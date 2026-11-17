package tn.kira.louage.stationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import tn.kira.louage.stationservice.config.CacheConfig;
import tn.kira.louage.stationservice.controller.StationController;
import tn.kira.louage.stationservice.entity.Station;
import tn.kira.louage.stationservice.repository.StationRepository;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = StationCacheTest.TestConfig.class)
class StationCacheTest {

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        StationRepository repository() {
            return mock(StationRepository.class);
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CacheConfig.STATIONS_CACHE);
        }

        @Bean
        StationQueryService queryService(StationRepository repository) {
            return new StationQueryService(repository);
        }

        @Bean
        StationController controller(StationRepository repository, StationQueryService queryService) {
            return new StationController(repository, queryService);
        }
    }

    @Autowired
    private StationRepository repository;

    @Autowired
    private StationQueryService queryService;

    @Autowired
    private StationController controller;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void resetState() {
        reset(repository);
        cacheManager.getCache(CacheConfig.STATIONS_CACHE).clear();
    }

    private static Station station(Long id, String name) {
        return Station.builder()
                .id(id)
                .name(name)
                .city("City")
                .governorate("Governorate")
                .build();
    }

    @Test
    void secondReadIsServedFromCache() {
        when(repository.findById(1L)).thenReturn(Optional.of(station(1L, "Old")));

        queryService.byId(1L);
        queryService.byId(1L);

        verify(repository, times(1)).findById(1L);
    }

    @Test
    void listIsServedFromCache() {
        when(repository.findAll()).thenReturn(List.of(station(1L, "Old")));

        assertThat(queryService.all()).hasSize(1);
        assertThat(queryService.all()).hasSize(1);

        verify(repository, times(1)).findAll();
    }

    @Test
    void updateEvictsCache() {
        when(repository.findById(1L)).thenReturn(Optional.of(station(1L, "Old")));
        when(repository.save(any(Station.class))).thenAnswer(invocation -> invocation.getArgument(0));

        queryService.byId(1L);
        controller.update(1L, Station.builder().name("New").city("New City").governorate("New Gov").build());
        Station afterUpdate = queryService.byId(1L);

        assertThat(afterUpdate.getName()).isEqualTo("New");
        verify(repository, times(3)).findById(1L);
    }

    @Test
    void deleteEvictsCache() {
        when(repository.findById(1L)).thenReturn(Optional.of(station(1L, "Old")));
        when(repository.existsById(1L)).thenReturn(true);

        queryService.byId(1L);
        controller.delete(1L);
        queryService.byId(1L);

        verify(repository, times(2)).findById(1L);
    }
}
