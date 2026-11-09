package tn.kira.louage.tripservice.client.fallback;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

/**
 * Decides whether a Feign failure is an infrastructure/availability problem
 * (safe to answer with a fallback) or a genuine error that must surface.
 */
final class FallbackCause {

    private FallbackCause() {
    }

    static boolean isServiceUnavailable(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof CallNotPermittedException
                    || current instanceof RetryableException
                    || current instanceof IOException
                    || current instanceof TimeoutException) {
                return true;
            }
            if (current instanceof FeignException feignException) {
                int status = feignException.status();
                // status 0 = connection failure; 5xx = remote service failure
                return status == 0 || status >= 500;
            }
            current = current.getCause();
        }
        return false;
    }
}
