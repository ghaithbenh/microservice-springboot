package tn.kira.louage.tripservice.client.fallback;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

class FallbackCauseTest {

    @Test
    void connectionFailureIsServiceUnavailable() {
        assertThat(FallbackCause.isServiceUnavailable(new IOException("connection refused"))).isTrue();
    }

    @Test
    void timeoutIsServiceUnavailable() {
        assertThat(FallbackCause.isServiceUnavailable(new TimeoutException("timed out"))).isTrue();
    }

    @Test
    void openCircuitIsServiceUnavailable() {
        CallNotPermittedException ex =
                CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("test"));
        assertThat(FallbackCause.isServiceUnavailable(ex)).isTrue();
    }

    @Test
    void retryableFeignFailureIsServiceUnavailable() {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "http://localhost/test",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8);
        RetryableException ex = new RetryableException(
                -1, "retry", Request.HttpMethod.GET, (Throwable) null, (Long) null, request);
        assertThat(FallbackCause.isServiceUnavailable(ex)).isTrue();
    }

    @Test
    void serverErrorIsServiceUnavailable() {
        assertThat(FallbackCause.isServiceUnavailable(TestFeignExceptions.status(503))).isTrue();
        assertThat(FallbackCause.isServiceUnavailable(TestFeignExceptions.status(500))).isTrue();
    }

    @Test
    void clientErrorIsNotServiceUnavailable() {
        assertThat(FallbackCause.isServiceUnavailable(TestFeignExceptions.status(400))).isFalse();
        assertThat(FallbackCause.isServiceUnavailable(TestFeignExceptions.status(404))).isFalse();
        assertThat(FallbackCause.isServiceUnavailable(TestFeignExceptions.status(409))).isFalse();
    }

    @Test
    void programmingErrorIsNotServiceUnavailable() {
        assertThat(FallbackCause.isServiceUnavailable(new IllegalArgumentException("bug"))).isFalse();
        assertThat(FallbackCause.isServiceUnavailable(new NullPointerException())).isFalse();
    }

    @Test
    void wrappedInfrastructureFailureIsDetectedThroughCauseChain() {
        RuntimeException wrapper = new RuntimeException("wrapped", new IOException("down"));
        assertThat(FallbackCause.isServiceUnavailable(wrapper)).isTrue();
    }
}
