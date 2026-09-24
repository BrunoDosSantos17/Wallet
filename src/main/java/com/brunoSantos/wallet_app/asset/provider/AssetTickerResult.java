package com.brunoSantos.wallet_app.asset.provider;

public record AssetTickerResult(
        String requestedSymbol,
        String symbol,
        Boolean changed,
        String name,
        String longName,
        String assetType,
        String subType,
        String exchange,
        String currency,
        String sector,
        String subsector,
        Boolean isActive
) {}
