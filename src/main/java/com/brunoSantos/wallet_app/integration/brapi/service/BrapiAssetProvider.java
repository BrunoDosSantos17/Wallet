package com.brunoSantos.wallet_app.integration.brapi.service;

import com.brunoSantos.wallet_app.asset.provider.AssetProvider;
import com.brunoSantos.wallet_app.asset.provider.AssetTickerResult;
import com.brunoSantos.wallet_app.integration.brapi.client.BrapiClient;
import com.brunoSantos.wallet_app.shared.exception.TicketNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class BrapiAssetProvider implements AssetProvider {

    private final BrapiClient brapiClient;

    @Value("${brapi.token}")
    private String token;

    @Override
    public BigDecimal getPrice(String ticker) {
        var response = brapiClient.getQuote(ticker, token);
        return response.results()
                .getFirst()
                .regularMarketPrice();
    }

    @Override
    public AssetTickerResult validate(String ticker) {
        var response = brapiClient.getTickerInfo(ticker, 1, token);

        if (response.results() == null || response.results().isEmpty()) {
            throw new TicketNotFoundException(404, "Ticker não localizado: " + ticker);
        }

        var result = response.results().getFirst();

        if (!result.symbol().equalsIgnoreCase(ticker)
                && result.isActive()) {
            throw new TicketNotFoundException(404, "Ticker não localizado: " + ticker);
        }

        return new AssetTickerResult(
                result.requestedSymbol(),
                result.symbol(),
                result.changed(),
                result.name(),
                result.longName(),
                result.assetType(),
                result.subType(),
                result.exchange(),
                result.currency(),
                result.sector(),
                result.subsector(),
                result.isActive(),
                result.quote() != null ? result.quote().lastPrice() : null
        );
    }
}
