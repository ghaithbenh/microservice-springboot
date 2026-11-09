package tn.kira.louage.tripservice.client.fallback;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import tn.kira.louage.tripservice.client.LouageClient;

@Component
public class LouageClientFallbackFactory implements FallbackFactory<LouageClient> {

    private final LouageClientFallback fallback;

    public LouageClientFallbackFactory(LouageClientFallback fallback) {
        this.fallback = fallback;
    }

    @Override
    public LouageClient create(Throwable cause) {
        if (FallbackCause.isServiceUnavailable(cause)) {
            return fallback;
        }
        return id -> {
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        };
    }
}
