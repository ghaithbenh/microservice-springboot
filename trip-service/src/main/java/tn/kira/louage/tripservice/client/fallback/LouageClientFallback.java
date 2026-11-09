package tn.kira.louage.tripservice.client.fallback;

import org.springframework.stereotype.Component;

import tn.kira.louage.tripservice.client.LouageClient;
import tn.kira.louage.tripservice.dto.Louage;

@Component
public class LouageClientFallback implements LouageClient {

    @Override
    public Louage getLouage(Long id) {
        return Louage.builder()
                .id(id)
                .plateNumber("Unavailable")
                .capacity(0)
                .driverName("Info temporarily unavailable")
                .status("UNKNOWN")
                .build();
    }
}
