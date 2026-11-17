package tn.kira.louage.gateway.config;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitResponseFilter implements WebFilter {

    private static final byte[] BODY = ("{\"status\":429,\"error\":\"Too Many Requests\","
            + "\"message\":\"Rate limit exceeded. Please slow down and retry.\"}")
            .getBytes(StandardCharsets.UTF_8);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(exchange.getResponse()) {
            private final AtomicBoolean written = new AtomicBoolean();

            @Override
            public Mono<Void> setComplete() {
                HttpStatusCode status = getStatusCode();
                boolean rateLimited = status != null && status.value() == 429
                        && getHeaders().containsKey("X-RateLimit-Remaining");
                if (rateLimited && written.compareAndSet(false, true)) {
                    getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    getHeaders().setContentLength(BODY.length);
                    DataBuffer buffer = bufferFactory().wrap(BODY);
                    return super.writeWith(Mono.just(buffer));
                }
                return super.setComplete();
            }
        };
        return chain.filter(exchange.mutate().response(decorated).build());
    }
}
