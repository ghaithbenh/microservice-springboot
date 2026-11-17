package tn.kira.louage.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

class GatewayRateLimitTest {

    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final KeyResolver keyResolver = exchange -> Mono.just("passenger");

    private GatewayFilter filter() {
        RequestRateLimiterGatewayFilterFactory.Config config =
                new RequestRateLimiterGatewayFilterFactory.Config();
        config.setRateLimiter(rateLimiter);
        config.setKeyResolver(keyResolver);
        return new RequestRateLimiterGatewayFilterFactory(rateLimiter, keyResolver).apply(config);
    }

    private static MockServerWebExchange exchangeWithRoute() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/louages").build());
        Route route = Route.async()
                .id("louage-service")
                .uri(URI.create("lb://louage-service"))
                .predicate(exchange1 -> true)
                .build();
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);
        return exchange;
    }

    @Test
    void allowsRequestWhenTokensRemain() {
        when(rateLimiter.isAllowed(anyString(), anyString()))
                .thenReturn(Mono.just(new RateLimiter.Response(true, Map.of("X-RateLimit-Remaining", "19"))));
        MockServerWebExchange exchange = exchangeWithRoute();
        AtomicBoolean proceeded = new AtomicBoolean();
        GatewayFilterChain chain = e -> {
            proceeded.set(true);
            return Mono.empty();
        };

        filter().filter(exchange, chain).block();

        assertThat(proceeded).isTrue();
    }

    @Test
    void returns429WhenLimitExceeded() {
        when(rateLimiter.isAllowed(anyString(), anyString()))
                .thenReturn(Mono.just(new RateLimiter.Response(false, Map.of("X-RateLimit-Remaining", "0"))));
        MockServerWebExchange exchange = exchangeWithRoute();
        AtomicBoolean proceeded = new AtomicBoolean();
        GatewayFilterChain chain = e -> {
            proceeded.set(true);
            return Mono.empty();
        };

        filter().filter(exchange, chain).block();

        assertThat(proceeded).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void keyResolverFallsBackToForwardedIpWhenAnonymous() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/louages")
                .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1")
                .build());

        assertThat(new RateLimitConfig().userKeyResolver().resolve(exchange).block()).isEqualTo("203.0.113.7");
    }
}
