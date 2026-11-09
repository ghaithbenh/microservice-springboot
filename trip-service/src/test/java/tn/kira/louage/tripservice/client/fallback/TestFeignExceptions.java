package tn.kira.louage.tripservice.client.fallback;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import feign.FeignException;
import feign.Request;
import feign.Response;

/**
 * Builds real {@link FeignException} instances for unit tests without a network call.
 */
final class TestFeignExceptions {

    private TestFeignExceptions() {
    }

    static FeignException status(int status) {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "http://localhost/test",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8);
        Response response = Response.builder()
                .status(status)
                .reason("test")
                .request(request)
                .headers(Collections.emptyMap())
                .body(new byte[0])
                .build();
        return FeignException.errorStatus("TestClient#method", response);
    }
}
