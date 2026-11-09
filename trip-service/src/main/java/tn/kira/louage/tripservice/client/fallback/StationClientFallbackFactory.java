package tn.kira.louage.tripservice.client.fallback;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import tn.kira.louage.tripservice.client.StationClient;

@Component
public class StationClientFallbackFactory implements FallbackFactory<StationClient> {

    private final StationClientFallback fallback;

    public StationClientFallbackFactory(StationClientFallback fallback) {
        this.fallback = fallback;
    }

    @Override
    public StationClient create(Throwable cause) {
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
