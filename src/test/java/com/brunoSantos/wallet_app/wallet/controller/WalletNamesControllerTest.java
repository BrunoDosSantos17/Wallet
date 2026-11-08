package com.brunoSantos.wallet_app.wallet.controller;

import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.wallet.service.WalletService;
import com.brunoSantos.wallet_app.asset.service.AssetPriceScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({WalletController.class, WalletApiController.class})
class WalletNamesControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private WalletService walletService;
    @MockitoBean private AssetPriceScheduler assetPriceScheduler;

    @Test
    void returnsNamesWithAndWithoutProxyPrefix() throws Exception {
        when(walletService.findAll()).thenReturn(List.of(
                Wallet.builder().name("Principal").build(),
                Wallet.builder().name("Reserva").build()));
        for (String path : List.of("/api/wallet/all", "/wallet/all")) {
            mockMvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(content().json("[\"Principal\",\"Reserva\"]"));
        }
    }

    @Test
    void returnsEmptyArrayWhenThereAreNoWallets() throws Exception {
        when(walletService.findAll()).thenReturn(List.of());
        mockMvc.perform(get("/api/wallet/all")).andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }
}
