package com.brunoSantos.wallet_app.asset.service;

import com.brunoSantos.wallet_app.asset.domain.Asset;
import com.brunoSantos.wallet_app.asset.repository.AssetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AssetPriceSchedulerTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AssetPriceService assetPriceService;

    @Mock
    private TaskScheduler taskScheduler;

    @InjectMocks
    private AssetPriceScheduler assetPriceScheduler;

    @Test
    void should_update_price_for_every_registered_asset() {
        when(assetRepository.findAll()).thenReturn(List.of(
                Asset.builder().ticker("PETR4").build(),
                Asset.builder().ticker("VALE3").build()
        ));

        assetPriceScheduler.updateAssetPrices();

        verify(assetPriceService).updatePrice("PETR4");
        verify(assetPriceService).updatePrice("VALE3");
    }

    @Test
    void should_continue_updating_assets_after_one_ticker_fails() {
        when(assetRepository.findAll()).thenReturn(List.of(
                Asset.builder().ticker("PETR4").build(),
                Asset.builder().ticker("VALE3").build()
        ));
        doThrow(new IllegalStateException("Brapi indisponível"))
                .when(assetPriceService).updatePrice("PETR4");

        assetPriceScheduler.updateAssetPrices();

        verify(assetPriceService).updatePrice("PETR4");
        verify(assetPriceService).updatePrice("VALE3");
    }

    @Test
    void should_cancel_scheduled_price_updates_when_disabled() {
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> future = org.mockito.Mockito.mock(ScheduledFuture.class);
        org.mockito.Mockito.doReturn(future).when(taskScheduler).scheduleWithFixedDelay(
                org.mockito.ArgumentMatchers.any(Runnable.class),
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.any(Duration.class)
        );
        when(future.cancel(false)).thenReturn(true);
        assetPriceScheduler.start();

        boolean disabled = assetPriceScheduler.disable();

        org.assertj.core.api.Assertions.assertThat(disabled).isTrue();
        verify(future).cancel(false);
    }

    @Test
    void should_schedule_updates_again_after_being_disabled() {
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> future = org.mockito.Mockito.mock(ScheduledFuture.class);
        org.mockito.Mockito.doReturn(future).when(taskScheduler).scheduleWithFixedDelay(
                org.mockito.ArgumentMatchers.any(Runnable.class),
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.any(Duration.class)
        );
        when(future.cancel(false)).thenReturn(true);

        assetPriceScheduler.start();
        assetPriceScheduler.disable();
        assetPriceScheduler.start();

        verify(taskScheduler, org.mockito.Mockito.times(2)).scheduleWithFixedDelay(
                org.mockito.ArgumentMatchers.any(Runnable.class),
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.any(Duration.class)
        );
    }

    @Test
    void should_not_cancel_task_when_scheduler_was_not_started() {
        org.assertj.core.api.Assertions.assertThat(assetPriceScheduler.disable()).isFalse();
        verify(taskScheduler, never()).scheduleWithFixedDelay(
                org.mockito.ArgumentMatchers.any(Runnable.class),
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.any(Duration.class)
        );
    }
}
