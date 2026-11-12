package com.brunoSantos.wallet_app.wallet.service;

import com.brunoSantos.wallet_app.asset.domain.Asset;
import com.brunoSantos.wallet_app.asset.domain.AssetType;
import com.brunoSantos.wallet_app.position.domain.AssetPosition;
import com.brunoSantos.wallet_app.position.repository.AssetPositionRepository;
import com.brunoSantos.wallet_app.asset.service.AssetPriceService;
import com.brunoSantos.wallet_app.dividend.repository.DividendoRecebidoRepository;
import com.brunoSantos.wallet_app.integration.awesomeapi.client.AwesomeApiClient;
import com.brunoSantos.wallet_app.integration.awesomeapi.dto.RawCurrencyQuote;
import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.wallet.exception.WalletNotFoundException;
import com.brunoSantos.wallet_app.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private AssetPositionRepository assetPositionRepository;

    @Mock
    private AssetPriceService assetPriceService;

    @Mock
    private DividendoRecebidoRepository dividendRepository;

    @Mock
    private AwesomeApiClient awesomeApiClient;

    @InjectMocks
    private WalletService walletService;

    @Test
    void should_return_basic_wallet_info_with_market_value_distribution() {
        Wallet wallet = Wallet.builder().id(1L).name("Principal").build();
        Asset petr4 = Asset.builder()
                .ticker("PETR4")
                .name("Petrobras")
                .type(AssetType.STOCK)
                .currentPrice(new BigDecimal("40.00"))
                .build();
        Asset vale3 = Asset.builder()
                .ticker("VALE3")
                .name("Vale")
                .type(AssetType.STOCK)
                .currentPrice(new BigDecimal("60.00"))
                .build();
        Asset hglg11 = Asset.builder()
                .ticker("HGLG11")
                .name("Fundo Logístico")
                .type(AssetType.FII)
                .currentPrice(new BigDecimal("50.00"))
                .build();
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(assetPositionRepository.findAllByWallet(wallet)).thenReturn(List.of(
                AssetPosition.builder().asset(petr4).quantity(new BigDecimal("2")).build(),
                AssetPosition.builder().asset(vale3).quantity(BigDecimal.ONE).build(),
                AssetPosition.builder().asset(hglg11).quantity(new BigDecimal("20")).build()
        ));

        var response = walletService.infoBasica(1L);

        assertThat(response.patrimonioTotal()).isEqualByComparingTo("1140.00");
        assertThat(response.rendaMensalEst()).isEqualByComparingTo("0.00");
        assertThat(response.ativosCarteira()).isEqualTo(3);
        assertThat(response.distribuicao()).containsExactly(
                new com.brunoSantos.wallet_app.wallet.dto.WalletBasicInfoResponse.AssetDistribution(
                        "FII", "87,72%", "1000", "20", 1),
                new com.brunoSantos.wallet_app.wallet.dto.WalletBasicInfoResponse.AssetDistribution(
                        "STOCK", "12,28%", "140", "3", 2)
        );
    }

    @Test
    void preserves_fractional_units_and_counts_positions_with_missing_prices() {
        Wallet wallet = Wallet.builder().id(1L).build();
        Asset unknown = Asset.builder().ticker("A").currentPrice(null).build();
        Asset zero = Asset.builder().ticker("B").currentPrice(BigDecimal.ZERO).build();
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(assetPositionRepository.findAllByWallet(wallet)).thenReturn(List.of(
                AssetPosition.builder().asset(unknown).quantity(new BigDecimal("1.123456789012")).build(),
                AssetPosition.builder().asset(zero).quantity(new BigDecimal("2.5")).build(),
                AssetPosition.builder().asset(zero).quantity(BigDecimal.ZERO).build()));
        when(dividendRepository.sumByWalletAndPeriodAndCurrency(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("BRL"))).thenReturn(new BigDecimal("14.123"));
        var result = walletService.infoBasica(1L);
        assertThat(result.patrimonioTotal()).isEqualByComparingTo("0.00");
        assertThat(result.rendaMensalEst()).isEqualByComparingTo("14.12");
        assertThat(result.ativosCarteira()).isEqualTo(2);
        assertThat(result.distribuicao()).containsExactly(
                new com.brunoSantos.wallet_app.wallet.dto.WalletBasicInfoResponse.AssetDistribution("OUTROS", "0,00%", "0", "3.623456789012", 2));
        var month = java.time.YearMonth.now(java.time.ZoneId.of("America/Sao_Paulo"));
        verify(dividendRepository).sumByWalletAndPeriodAndCurrency(1L, month.atDay(1), month.plusMonths(1).atDay(1), "BRL");
    }

    @Test
    void converts_usd_at_unrounded_bid_and_reports_nonblocking_failure() {
        Wallet wallet = Wallet.builder().id(1L).build();
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(assetPositionRepository.findAllByWallet(wallet)).thenReturn(List.of());
        when(dividendRepository.sumByWalletAndPeriodAndCurrency(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("BRL"))).thenReturn(BigDecimal.TEN);
        when(dividendRepository.sumByWalletAndPeriodAndCurrency(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("USD"))).thenReturn(new BigDecimal("2"));
        when(awesomeApiClient.getRawQuote("USD-BRL"))
                .thenReturn(java.util.Map.of("USDBRL", new RawCurrencyQuote("USD", "BRL", new BigDecimal("5.1234"))))
                .thenThrow(new IllegalStateException("sem cotação"));
        assertThat(walletService.infoBasica(1L).rendaMensalEst()).isEqualByComparingTo("20.25");
        var result = walletService.infoBasica(1L);
        assertThat(result.rendaMensalEst()).isEqualByComparingTo("10.00");
        assertThat(result.avisos()).containsExactly("ConversaoMoedaIndisponivel");
    }

    @Test
    void should_return_zero_totals_for_wallet_without_positions() {
        Wallet wallet = Wallet.builder().id(1L).name("Vazia").build();
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(assetPositionRepository.findAllByWallet(wallet)).thenReturn(List.of());

        var response = walletService.infoBasica(1L);

        assertThat(response.patrimonioTotal()).isEqualByComparingTo("0.00");
        assertThat(response.rendaMensalEst()).isEqualByComparingTo("0.00");
        assertThat(response.ativosCarteira()).isZero();
        assertThat(response.distribuicao()).isEmpty();
    }

    @Test
    void should_throw_when_wallet_does_not_exist() {
        when(walletRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.infoBasica(99L))
                .isInstanceOf(WalletNotFoundException.class);
    }

    @Test
    void should_refresh_wallet_assets_once_per_ticker_before_returning_basic_info() {
        Wallet wallet = Wallet.builder().id(1L).name("Principal").build();
        Asset petr4 = Asset.builder().ticker("PETR4").name("Petrobras").build();
        AssetPosition firstPosition = AssetPosition.builder()
                .asset(petr4)
                .quantity(new BigDecimal("10"))
                .build();
        AssetPosition secondPosition = AssetPosition.builder()
                .asset(petr4)
                .quantity(new BigDecimal("5"))
                .build();
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(assetPositionRepository.findAllByWallet(wallet))
                .thenReturn(List.of(firstPosition, secondPosition));

        walletService.atualizarInfoBasica(1L);

        verify(assetPriceService, times(1)).updatePrice("PETR4");
        verify(assetPositionRepository, times(2)).findAllByWallet(wallet);
    }
}
