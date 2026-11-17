package tn.kira.louage.louageservice.service;

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

import tn.kira.louage.louageservice.config.CacheConfig;
import tn.kira.louage.louageservice.controller.LouageController;
import tn.kira.louage.louageservice.entity.Louage;
import tn.kira.louage.louageservice.repository.LouageRepository;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = LouageCacheTest.TestConfig.class)
class LouageCacheTest {

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        LouageRepository repository() {
            return mock(LouageRepository.class);
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CacheConfig.LOUAGES_CACHE);
        }

        @Bean
        LouageQueryService queryService(LouageRepository repository) {
            return new LouageQueryService(repository);
        }

        @Bean
        LouageController controller(LouageRepository repository, LouageQueryService queryService) {
            return new LouageController(repository, queryService);
        }
    }

    @Autowired
    private LouageRepository repository;

    @Autowired
    private LouageQueryService queryService;

    @Autowired
    private LouageController controller;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void resetState() {
        reset(repository);
        cacheManager.getCache(CacheConfig.LOUAGES_CACHE).clear();
    }

    private static Louage louage(Long id, String plate, int capacity, Louage.Status status) {
        return Louage.builder()
                .id(id)
                .plateNumber(plate)
                .capacity(capacity)
                .driverName("Driver")
                .status(status)
                .build();
    }

    @Test
    void secondReadIsServedFromCache() {
        when(repository.findById(1L)).thenReturn(Optional.of(louage(1L, "OLD", 9, Louage.Status.AVAILABLE)));

        queryService.byId(1L);
        queryService.byId(1L);

        verify(repository, times(1)).findById(1L);
    }

    @Test
    void listIsServedFromCache() {
        when(repository.findAll()).thenReturn(List.of(louage(1L, "OLD", 9, Louage.Status.AVAILABLE)));

        assertThat(queryService.all()).hasSize(1);
        assertThat(queryService.all()).hasSize(1);

        verify(repository, times(1)).findAll();
    }

    @Test
    void updateEvictsCache() {
        Louage existing = louage(1L, "OLD", 9, Louage.Status.AVAILABLE);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(Louage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        queryService.byId(1L);
        controller.update(1L, louage(null, "NEW", 8, Louage.Status.ON_ROAD));
        Louage afterUpdate = queryService.byId(1L);

        assertThat(afterUpdate.getPlateNumber()).isEqualTo("NEW");
        verify(repository, times(3)).findById(1L);
    }

    @Test
    void deleteEvictsCache() {
        when(repository.findById(1L)).thenReturn(Optional.of(louage(1L, "OLD", 9, Louage.Status.AVAILABLE)));
        when(repository.existsById(1L)).thenReturn(true);

        queryService.byId(1L);
        controller.delete(1L);
        queryService.byId(1L);

        verify(repository, times(2)).findById(1L);
    }
}
