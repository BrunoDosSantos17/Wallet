package com.brunoSantos.wallet_app.imports.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class ImportExecutorConfigurationTest {
    @Test
    void bounds_pool_and_queue_and_rejects_instead_of_running_on_request_thread() throws Exception {
        var executor = (ThreadPoolTaskExecutor) new ImportExecutorConfiguration().importExecutor();
        executor.initialize();
        var active = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        Runnable blocking = () -> {
            active.countDown();
            try { release.await(); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        };
        try {
            for (int i = 0; i < 4; i++) executor.execute(blocking);
            assertThat(active.await(5, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 100; i++) executor.execute(() -> {});
            assertThat(executor.getPoolSize()).isEqualTo(4);
            assertThat(executor.getMaxPoolSize()).isEqualTo(4);
            assertThat(executor.getThreadPoolExecutor().getQueue()).hasSize(100);
            assertThatThrownBy(() -> executor.execute(() -> fail("must not run on caller")))
                    .isInstanceOf(RejectedExecutionException.class);
        } finally {
            release.countDown();
            executor.shutdown();
        }
        assertThatThrownBy(() -> executor.execute(() -> fail("must not run after shutdown")))
                .isInstanceOf(RejectedExecutionException.class);
    }
}
