package com.brunoSantos.wallet_app.asset.service;

import com.brunoSantos.wallet_app.asset.domain.Asset;
import com.brunoSantos.wallet_app.asset.repository.AssetRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssetPriceScheduler {

    private final AssetRepository assetRepository;
    private final AssetPriceService assetPriceService;
    private final TaskScheduler taskScheduler;

    @Value("${wallet.assets.price-update-delay:300000}")
    private long updateDelayMillis;

    @Value("${wallet.assets.price-update-initial-delay:300000}")
    private long initialDelayMillis;

    private ScheduledFuture<?> scheduledTask;
    private boolean enabled;

    @PostConstruct
    public synchronized void start() {
        if (!enabled) {
            Instant firstExecution = Instant.now().plusMillis(initialDelayMillis);
            scheduledTask = taskScheduler.scheduleWithFixedDelay(
                    this::updateAssetPrices,
                    firstExecution,
                    Duration.ofMillis(updateDelayMillis)
            );
            enabled = true;
        }
    }

    public void updateAssetPrices() {
        for (Asset asset : assetRepository.findAll()) {
            try {
                assetPriceService.updatePrice(asset.getTicker());
            } catch (Exception exception) {
                log.warn("Falha ao atualizar preço do ativo {} pela Brapi", asset.getTicker(), exception);
            }
        }
    }

    public synchronized boolean disable() {
        if (!enabled || scheduledTask == null) {
            return false;
        }
        boolean cancelled = scheduledTask.cancel(false);
        if (cancelled) {
            enabled = false;
            log.info("Atualização automática de preços dos ativos desativada");
        }
        return cancelled;
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    @PreDestroy
    public synchronized void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            enabled = false;
        }
    }
}
