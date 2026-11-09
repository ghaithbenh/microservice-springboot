package tn.kira.louage.tripservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import tn.kira.louage.tripservice.client.fallback.LouageClientFallbackFactory;
import tn.kira.louage.tripservice.dto.Louage;

@FeignClient(name = "louage-service", fallbackFactory = LouageClientFallbackFactory.class, dismiss404 = true)
public interface LouageClient {

    @GetMapping("/louages/{id}")
    Louage getLouage(@PathVariable("id") Long id);
}
