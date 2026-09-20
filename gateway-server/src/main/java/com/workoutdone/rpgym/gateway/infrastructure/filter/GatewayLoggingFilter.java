package com.workoutdone.rpgym.gateway.infrastructure.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class GatewayLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();
        long startTime = System.nanoTime();

        log.info(
                "Gateway request: {} {}",
                method,
                path
        );

        return chain.filter(exchange)
                .doFinally(signal -> {
                    long latency = (System.nanoTime() - startTime) / 1_000_000;

                    log.info(
                            "Gateway response: {} {} {}ms {}",
                            method,
                            path,
                            latency,
                            exchange.getResponse().getStatusCode()
                    );
                });
    }

    @Override
    public int getOrder() {
        // NettyRoutingFilter보다 먼저 실행되도록 순서를 명시
        return Ordered.LOWEST_PRECEDENCE - 1;
    }

}