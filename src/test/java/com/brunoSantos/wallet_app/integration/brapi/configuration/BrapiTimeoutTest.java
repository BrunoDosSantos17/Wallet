package com.brunoSantos.wallet_app.integration.brapi.configuration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;

class BrapiTimeoutTest {
    @Test
    void configures_explicit_timeouts_and_times_out_against_local_slow_provider() throws Exception {
        var configuration = new BrapiConfig();
        var requests = configuration.requestFactory();
        assertThat(ReflectionTestUtils.getField(requests, "connectTimeout")).isEqualTo(3000);
        assertThat(ReflectionTestUtils.getField(requests, "readTimeout")).isEqualTo(10000);
        assertThat(configuration.brapiClient()).isNotNull();
        ReflectionTestUtils.setField(configuration, "token", "synthetic-not-used");
        assertThat(configuration.brapiToken()).isEqualTo("synthetic-not-used");

        var release = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try (var workers = Executors.newSingleThreadExecutor()) {
            server.setExecutor(workers);
            server.createContext("/slow", exchange -> {
                try { release.await(); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                finally { exchange.close(); }
            });
            server.start();
            long start = System.nanoTime();
            try {
                var client = RestClient.builder().requestFactory(requests).build();
                assertThatThrownBy(() -> client.get().uri("http://127.0.0.1:" + server.getAddress().getPort() + "/slow")
                        .retrieve().body(String.class)).isInstanceOf(ResourceAccessException.class).hasRootCauseInstanceOf(SocketTimeoutException.class);
                assertThat(Duration.ofNanos(System.nanoTime() - start)).isBetween(Duration.ofSeconds(9), Duration.ofSeconds(20));
            } finally {
                release.countDown();
                server.stop(0);
            }
        }
    }
}
