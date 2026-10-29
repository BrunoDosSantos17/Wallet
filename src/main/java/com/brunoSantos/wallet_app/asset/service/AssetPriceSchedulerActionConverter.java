package com.brunoSantos.wallet_app.asset.service;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class AssetPriceSchedulerActionConverter implements Converter<String, AssetPriceSchedulerAction> {

    @Override
    public AssetPriceSchedulerAction convert(String source) {
        return AssetPriceSchedulerAction.valueOf(source.trim().toUpperCase(Locale.ROOT));
    }
}
