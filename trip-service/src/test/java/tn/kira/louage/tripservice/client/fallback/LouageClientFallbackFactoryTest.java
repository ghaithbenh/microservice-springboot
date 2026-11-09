package tn.kira.louage.tripservice.client.fallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import feign.FeignException;
import tn.kira.louage.tripservice.client.LouageClient;
import tn.kira.louage.tripservice.dto.Louage;

class LouageClientFallbackFactoryTest {

    private final LouageClientFallbackFactory factory =
            new LouageClientFallbackFactory(new LouageClientFallback());

    @Test
    void infrastructureFailureReturnsSafeDefaults() {
        LouageClient client = factory.create(new IOException("connection refused"));

        Louage louage = client.getLouage(42L);

        assertThat(louage.getId()).isEqualTo(42L);
        assertThat(louage.getPlateNumber()).isEqualTo("Unavailable");
        assertThat(louage.getCapacity()).isZero();
        assertThat(louage.getDriverName()).isEqualTo("Info temporarily unavailable");
        assertThat(louage.getStatus()).isEqualTo("UNKNOWN");
    }

    @Test
    void serverErrorReturnsSafeDefaults() {
        LouageClient client = factory.create(TestFeignExceptions.status(503));

        assertThat(client.getLouage(1L).getStatus()).isEqualTo("UNKNOWN");
    }

    @Test
    void clientErrorIsRethrownNotHidden() {
        FeignException error = TestFeignExceptions.status(400);
        LouageClient client = factory.create(error);

        assertThatThrownBy(() -> client.getLouage(1L)).isSameAs(error);
    }

    @Test
    void programmingErrorIsRethrownNotHidden() {
        IllegalArgumentException error = new IllegalArgumentException("bug");
        LouageClient client = factory.create(error);

        assertThatThrownBy(() -> client.getLouage(1L)).isSameAs(error);
    }

    @Test
    void checkedProgrammingErrorIsWrapped() {
        Exception cause = new Exception("bug");

        LouageClient client = factory.create(cause);

        assertThatThrownBy(() -> client.getLouage(1L))
                .isInstanceOf(RuntimeException.class)
                .hasCause(cause);
    }
}
