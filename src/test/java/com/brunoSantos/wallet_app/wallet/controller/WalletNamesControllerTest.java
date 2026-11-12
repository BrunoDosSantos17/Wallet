package com.brunoSantos.wallet_app.wallet.controller;

import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.wallet.dto.WalletBasicInfoResponse;
import java.math.BigDecimal;
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

    @Test
    void summary_serializes_decimal_units_without_losing_fraction() throws Exception {
        when(walletService.infoBasica(1L)).thenReturn(new WalletBasicInfoResponse(
                BigDecimal.ZERO, new BigDecimal("12.34"), 2,
                List.of(new WalletBasicInfoResponse.AssetDistribution("FII", "0,00%", "0", "1.123456789012", 2)), List.of()));
        mockMvc.perform(get("/wallet/infoBasica/1"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                    {"patrimonioTotal":0,"rendaMensalEst":12.34,"ativosCarteira":2,
                     "distribuicao":[{"nomeAtivo":"FII","percentual":"0,00%","valor":"0",
                                      "quantidade":"1.123456789012","quantidadeAtivos":2}]}
                    """));
    }
}
