package com.brunoSantos.wallet_app.integration.brapi.service;

import com.brunoSantos.wallet_app.asset.provider.AssetProvider;
import com.brunoSantos.wallet_app.asset.provider.AssetTickerResult;
import com.brunoSantos.wallet_app.integration.brapi.client.BrapiClient;
import com.brunoSantos.wallet_app.shared.exception.TicketNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class BrapiAssetProvider implements AssetProvider {

    private final BrapiClient brapiClient;

    @Value("${brapi.token}")
    private String token;

    @Override
    public BigDecimal getPrice(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new TicketNotFoundException(404, "Ticker não pode ser vazio");
        }
        var response = brapiClient.getQuote(ticker, token);
        if (response == null || response.results() == null || response.results().isEmpty()
                || response.results().getFirst() == null) {
            throw new TicketNotFoundException(404, "Cotação não localizada: " + ticker);
        }
        var price = response.results().getFirst().regularMarketPrice();
        if (price == null || price.signum() <= 0) {
            throw new TicketNotFoundException(404, "Cotação inválida para o ticker: " + ticker);
        }
        return price;
    }

    @Override
    public AssetTickerResult validate(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new TicketNotFoundException(404, "Ticker não pode ser vazio");
        }
        ticker = ticker.trim().toUpperCase(Locale.ROOT);
        var response = brapiClient.getTickerInfo(ticker, 1, token);

        if (response == null || response.results() == null || response.results().isEmpty()
                || response.results().getFirst() == null) {
            throw new TicketNotFoundException(404, "Ticker não localizado: " + ticker);
        }

        var result = response.results().getFirst();

        if (result.symbol() == null || !result.symbol().trim().equalsIgnoreCase(ticker.trim())
                || !Boolean.TRUE.equals(result.isActive())) {
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
