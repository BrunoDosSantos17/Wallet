package com.brunoSantos.wallet_app.wallet.controller;

import com.brunoSantos.wallet_app.asset.domain.Asset;
import com.brunoSantos.wallet_app.asset.domain.AssetType;
import com.brunoSantos.wallet_app.asset.service.AssetPriceScheduler;
import com.brunoSantos.wallet_app.position.domain.AssetPosition;
import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.wallet.exception.WalletNotFoundException;
import com.brunoSantos.wallet_app.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WalletController.class)
class WalletControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private WalletService walletService;
    @MockitoBean private AssetPriceScheduler assetPriceScheduler;

    @Test
    void get_wallet_serializes_existing_fields_and_market_fields_as_json_numbers_and_local_time() throws Exception {
        Asset asset = Asset.builder().ticker("PETR4").name("Petrobras").type(AssetType.STOCK)
                .currentPrice(new BigDecimal("40.00000000"))
                .lastUpdate(LocalDateTime.of(2026, 11, 14, 10, 15, 30)).build();
        when(walletService.findById(1L)).thenReturn(wallet(AssetPosition.builder().asset(asset)
                .quantity(new BigDecimal("1.500000000000"))
                .averagePrice(new BigDecimal("30.00000000")).build()));

        mockMvc.perform(get("/wallet/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Principal"))
                .andExpect(jsonPath("$.assetPositions[0].ticker").value("PETR4"))
                .andExpect(jsonPath("$.assetPositions[0].assetName").value("Petrobras"))
                .andExpect(jsonPath("$.assetPositions[0].assetType").value("STOCK"))
                .andExpect(jsonPath("$.assetPositions[0].quantity").isNumber())
                .andExpect(jsonPath("$.assetPositions[0].averagePrice").isNumber())
                .andExpect(jsonPath("$.assetPositions[0].totalCost").value(45.00))
                .andExpect(jsonPath("$.assetPositions[0].currentPrice").isNumber())
                .andExpect(jsonPath("$.assetPositions[0].marketValue").value(60.00))
                .andExpect(jsonPath("$.assetPositions[0].lastPriceUpdate").value("2026-11-14T10:15:30"));
    }

    @Test
    void get_wallet_serializes_missing_quote_as_null_and_zero_balance_as_number() throws Exception {
        Asset unknown = Asset.builder().ticker("SEM").lastUpdate(LocalDateTime.of(2026, 1, 1, 0, 0)).build();
        Asset zeroBalance = Asset.builder().ticker("ZERO").currentPrice(new BigDecimal("10.00")).build();
        when(walletService.findById(2L)).thenReturn(Wallet.builder().id(2L).name("Principal")
                .positions(List.of(
                        AssetPosition.builder().asset(unknown).quantity(BigDecimal.ONE).averagePrice(BigDecimal.ONE).build(),
                        AssetPosition.builder().asset(zeroBalance).quantity(BigDecimal.ZERO).averagePrice(BigDecimal.ZERO).build()))
                .build());

        mockMvc.perform(get("/wallet/2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.assetPositions[0].currentPrice").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.assetPositions[0].marketValue").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.assetPositions[0].lastPriceUpdate").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.assetPositions[1].marketValue").isNumber())
                .andExpect(jsonPath("$.assetPositions[1].marketValue").value(0.00));
    }

    @Test
    void get_empty_wallet_returns_empty_positions() throws Exception {
        when(walletService.findById(3L)).thenReturn(Wallet.builder().id(3L).name("Vazia").build());
        mockMvc.perform(get("/wallet/3")).andExpect(status().isOk())
                .andExpect(jsonPath("$.assetPositions").isEmpty());
    }

    @Test
    void get_missing_wallet_returns_not_found_with_existing_error_envelope() throws Exception {
        when(walletService.findById(99L)).thenThrow(new WalletNotFoundException());

        mockMvc.perform(get("/wallet/99")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Carteira não encontrada"));
    }

    @Test
    void other_wallet_endpoints_keep_existing_bad_request_mapping() throws Exception {
        when(walletService.infoBasica(99L)).thenThrow(new WalletNotFoundException());

        mockMvc.perform(get("/wallet/infoBasica/99")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Carteira não encontrada"));
    }

    private Wallet wallet(AssetPosition position) {
        return Wallet.builder().id(1L).name("Principal").positions(List.of(position)).build();
    }
}
