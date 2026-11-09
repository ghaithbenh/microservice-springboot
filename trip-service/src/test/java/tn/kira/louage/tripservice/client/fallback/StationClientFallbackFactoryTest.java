package tn.kira.louage.tripservice.client.fallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import feign.FeignException;
import tn.kira.louage.tripservice.client.StationClient;
import tn.kira.louage.tripservice.dto.Station;

class StationClientFallbackFactoryTest {

    private final StationClientFallbackFactory factory =
            new StationClientFallbackFactory(new StationClientFallback());

    @Test
    void infrastructureFailureReturnsSafeDefaults() {
        StationClient client = factory.create(new IOException("connection refused"));

        Station station = client.getStation(7L);

        assertThat(station.getId()).isEqualTo(7L);
        assertThat(station.getName()).isEqualTo("Unavailable");
        assertThat(station.getCity()).isEqualTo("Unavailable");
        assertThat(station.getGovernorate()).isEqualTo("Unavailable");
    }

    @Test
    void serverErrorReturnsSafeDefaults() {
        StationClient client = factory.create(TestFeignExceptions.status(503));

        assertThat(client.getStation(1L).getName()).isEqualTo("Unavailable");
    }

    @Test
    void clientErrorIsRethrownNotHidden() {
        FeignException error = TestFeignExceptions.status(400);
        StationClient client = factory.create(error);

        assertThatThrownBy(() -> client.getStation(1L)).isSameAs(error);
    }

    @Test
    void programmingErrorIsRethrownNotHidden() {
        IllegalStateException error = new IllegalStateException("bug");
        StationClient client = factory.create(error);

        assertThatThrownBy(() -> client.getStation(1L)).isSameAs(error);
    }
}
