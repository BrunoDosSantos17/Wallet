package com.brunoSantos.wallet_app.asset.service;

import com.brunoSantos.wallet_app.asset.domain.Asset;
import com.brunoSantos.wallet_app.asset.provider.AssetPriceProvider;
import com.brunoSantos.wallet_app.asset.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AssetPriceService {

    private final AssetRepository assetRepository;
    private final AssetPriceProvider priceProvider;

    public Asset updatePrice(String ticker) {

        var asset = assetRepository.findByTicker(ticker)
                .orElseThrow();

        var price = priceProvider.getPrice(ticker);

        asset.updatePrice(price, LocalDateTime.now());

        return assetRepository.save(asset);
    }

}
