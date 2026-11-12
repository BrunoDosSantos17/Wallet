package com.brunoSantos.wallet_app.dividend.service;

import com.brunoSantos.wallet_app.asset.domain.Asset;
import com.brunoSantos.wallet_app.asset.domain.AssetType;
import com.brunoSantos.wallet_app.dividend.domain.DividendoRecebido;
import com.brunoSantos.wallet_app.dividend.repository.DividendoRecebidoRepository;
import com.brunoSantos.wallet_app.integration.brapi.client.BrapiClient;
import com.brunoSantos.wallet_app.integration.brapi.service.BrapiDividendProvider;
import com.brunoSantos.wallet_app.integration.brapi.dto.BrapiDividendResponses.*;
import com.brunoSantos.wallet_app.position.domain.AssetPosition;
import com.brunoSantos.wallet_app.position.repository.AssetPositionRepository;
import com.brunoSantos.wallet_app.transaction.domain.Transaction;
import com.brunoSantos.wallet_app.transaction.domain.TransactionType;
import com.brunoSantos.wallet_app.transaction.repository.TransactionRepository;
import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DividendSyncServiceTest {
    @Mock AssetPositionRepository positions;
    @Mock WalletRepository wallets;
    @Mock TransactionRepository transactions;
    @Mock DividendoRecebidoRepository dividends;
    @Mock BrapiClient brapi;
    DividendSyncService service;
    Wallet wallet;
    AssetPosition position;

    @BeforeEach
    void setup() {
        service = new DividendSyncService(positions, wallets, transactions, dividends, List.of(new BrapiDividendProvider(brapi, "token")));
        wallet = Wallet.builder().id(1L).build();
        position = AssetPosition.builder().id(2L).wallet(wallet)
                .asset(Asset.builder().ticker("PETR4").type(AssetType.STOCK).build()).build();
        when(positions.findById(2L)).thenReturn(Optional.of(position));
        when(wallets.findByIdForUpdate(1L)).thenReturn(Optional.of(wallet));
    }

    private Event event(String rate) {
        return new Event(null, "JCP", "2024-01-10T03:00:00Z", "2024-02-01T03:00:00Z",
                null, "BR123", null, null, new BigDecimal(rate));
    }

    private void response(List<Event> events) {
        when(brapi.getStockDividends(eq("PETR4"), anyString(), anyString(), eq("true"), eq("Bearer token")))
                .thenReturn(new Stocks(List.of(new Result("PETR4", "PETR4", new Data(events)))));
    }

    @Test
    void calculates_on_record_date_not_current_position() {
        response(List.of(event("0.5")));
        when(transactions.findByWalletIdAndAssetTickerOrderByDataTransactionAscIdAsc(1L, "PETR4"))
                .thenReturn(List.of(
                        Transaction.builder().type(TransactionType.BUY).quantity(new BigDecimal("100.5")).dataTransaction(LocalDate.parse("2024-01-01")).build(),
                        Transaction.builder().type(TransactionType.SELL).quantity(new BigDecimal("50")).dataTransaction(LocalDate.parse("2024-01-20")).build()));
        var result = service.sync(2L, LocalDate.parse("2024-01-01"));
        var captor = ArgumentCaptor.forClass(DividendoRecebido.class);
        verify(dividends).save(captor.capture());
        assertThat(captor.getValue().getValorDividendo()).isEqualByComparingTo("50.25");
        assertThat(captor.getValue().getQuantidadeElegivel()).isEqualByComparingTo("100.5");
        assertThat(result.aviso()).contains("não créditos confirmados");
    }

    @Test
    void rejects_ambiguous_events() {
        response(List.of(event("0.5"), event("0.6")));
        when(transactions.findByWalletIdAndAssetTickerOrderByDataTransactionAscIdAsc(1L, "PETR4")).thenReturn(List.of());
        assertThatThrownBy(() -> service.sync(2L, LocalDate.parse("2024-01-01")))
                .hasMessageContaining("mesma data-base e tipo");
    }

    @Test
    void rejects_missing_unadjusted_rate() {
        response(List.of(new Event(null, "DIVIDENDO", "2024-01-10", "2024-02-01", null, null, null, null, null)));
        when(transactions.findByWalletIdAndAssetTickerOrderByDataTransactionAscIdAsc(1L, "PETR4")).thenReturn(List.of());
        assertThatThrownBy(() -> service.sync(2L, LocalDate.parse("2024-01-01")))
                .hasMessageContaining("Valor original por unidade indisponível");
        verify(dividends, never()).save(any());
    }

    @Test
    void repeated_event_is_not_inserted_twice() {
        response(List.of(event("0.5")));
        when(transactions.findByWalletIdAndAssetTickerOrderByDataTransactionAscIdAsc(1L, "PETR4"))
                .thenReturn(List.of(Transaction.builder().type(TransactionType.BUY).quantity(BigDecimal.TEN)
                        .dataTransaction(LocalDate.parse("2024-01-01")).build()));
        service.sync(2L, LocalDate.parse("2024-01-01"));
        var captor = ArgumentCaptor.forClass(DividendoRecebido.class);
        verify(dividends).save(captor.capture());
        when(dividends.findByAssetPositionIdAndIdentificadorOrigem(eq(2L), anyString())).thenReturn(Optional.of(captor.getValue()));
        assertThat(service.sync(2L, LocalDate.parse("2024-01-01")).existentes()).isEqualTo(1);
        verify(dividends, times(1)).save(any());
    }

    @Test
    void calculates_fii_income_using_raw_rate() {
        position.getAsset().setTicker("MXRF11");
        position.getAsset().setType(AssetType.FII);
        var event = new Event("MXRF11", "RENDIMENTO", "2024-01-10 00:00:00+00", "2024-02-01 00:00:00+00",
                null, null, null, null, new BigDecimal("0.1"));
        when(brapi.getFiiDividends(eq("MXRF11"), anyString(), anyString(), eq("Bearer token")))
                .thenReturn(new Fiis(List.of(event)));
        when(transactions.findByWalletIdAndAssetTickerOrderByDataTransactionAscIdAsc(1L, "MXRF11"))
                .thenReturn(List.of(Transaction.builder().type(TransactionType.BUY).quantity(BigDecimal.TEN)
                        .dataTransaction(LocalDate.parse("2024-01-01")).build()));
        service.sync(2L, LocalDate.parse("2024-01-01"));
        var captor = ArgumentCaptor.forClass(DividendoRecebido.class);
        verify(dividends).save(captor.capture());
        assertThat(captor.getValue().getTipoProvento().name()).isEqualTo("RENDIMENTO_FII");
        assertThat(captor.getValue().getValorDividendo()).isEqualByComparingTo("1");
    }
}
