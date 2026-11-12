package com.brunoSantos.wallet_app.transaction.service;

import com.brunoSantos.wallet_app.asset.domain.Asset;
import com.brunoSantos.wallet_app.asset.provider.AssetProvider;
import com.brunoSantos.wallet_app.asset.provider.AssetTickerResult;
import com.brunoSantos.wallet_app.asset.repository.AssetRepository;
import com.brunoSantos.wallet_app.integration.smarterdividends.service.SmarterAssetProvider;
import com.brunoSantos.wallet_app.position.domain.AssetPosition;
import com.brunoSantos.wallet_app.position.repository.AssetPositionRepository;
import com.brunoSantos.wallet_app.transaction.domain.Transaction;
import com.brunoSantos.wallet_app.transaction.domain.TransactionType;
import com.brunoSantos.wallet_app.transaction.dto.CreateTransactionRequest;
import com.brunoSantos.wallet_app.transaction.repository.TransactionRepository;
import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.wallet.exception.WalletNotFoundException;
import com.brunoSantos.wallet_app.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AssetPositionRepository positionRepository;

    @Mock
    private AssetProvider assetProvider;

    @Mock private SmarterAssetProvider smarterAssetProvider;

    private TransactionService transactionService;

    private Wallet wallet;
    private Asset asset;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(transactionRepository, walletRepository, assetRepository,
                positionRepository, assetProvider, smarterAssetProvider);
        wallet = Wallet.builder().id(1L).name("Main").build();

        asset = Asset.builder()
                .ticker("PETR4")
                .name("PETR4")
                .currentPrice(BigDecimal.ZERO)
                .lastUpdate(LocalDateTime.now())
                .build();
        lenient().when(assetProvider.validate(anyString())).thenAnswer(invocation -> {
            String ticker = invocation.getArgument(0);
            return new AssetTickerResult(ticker, ticker, false, ticker, ticker, "stock", "stock",
                    "B3", "BRL", null, null, true, null);
        });
    }

    @Test
    void hash_collision_is_reported_separately_from_duplicate() {
        var request = new CreateTransactionRequest(1L, "PETR4", TransactionType.BUY, "stock", null,
                BigDecimal.ONE, BigDecimal.TEN, "01/01/2024");
        var info = new AssetTickerResult("PETR4", "PETR4", false, "PETR4", "PETR4",
                "stock", "stock", "B3", "BRL", null, null, true, null);
        var previous = Transaction.builder().asset(asset).type(TransactionType.BUY)
                .dataTransaction(java.time.LocalDate.of(2024, 1, 1))
                .quantity(BigDecimal.TWO).price(BigDecimal.TEN).build();
        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByWalletIdAndImportIdentity(eq(1L), anyString())).thenReturn(Optional.of(previous));
        assertThatThrownBy(() -> transactionService.createImported(request, info))
                .hasMessageContaining("Colisão");
        verifyNoInteractions(positionRepository, assetRepository);
    }

    @Test
    void imported_duplicate_is_rejected_before_mutating_position() {
        var request = new CreateTransactionRequest(1L, "PETR4", TransactionType.BUY, "stock", null,
                BigDecimal.ONE, BigDecimal.TEN, "01/01/2024");
        var info = new AssetTickerResult("PETR4", "PETR4", false, "PETR4", "PETR4",
                "stock", "stock", "B3", "BRL", null, null, true, null);
        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        var previous = Transaction.builder().asset(asset).type(TransactionType.BUY)
                .dataTransaction(java.time.LocalDate.of(2024, 1, 1))
                .quantity(BigDecimal.ONE).price(BigDecimal.TEN).build();
        when(transactionRepository.findByWalletIdAndImportIdentity(eq(1L), anyString())).thenReturn(Optional.of(previous));
        assertThatThrownBy(() -> transactionService.createImported(request, info))
                .hasMessageContaining("duplicada");
        verifyNoInteractions(positionRepository, assetRepository);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void retroactive_operation_is_rejected_before_mutating_position() {
        var request = new CreateTransactionRequest(1L, "PETR4", TransactionType.BUY, "stock", null,
                BigDecimal.ONE, BigDecimal.TEN, "01/01/2024");
        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findLatestDate(1L, "PETR4")).thenReturn(java.time.LocalDate.of(2024, 1, 2));
        assertThatThrownBy(() -> transactionService.create(request)).hasMessageContaining("retroativa");
        verifyNoInteractions(positionRepository, assetRepository);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void should_create_buy_transaction_when_asset_and_position_exist() {

        var request = new CreateTransactionRequest(
                1L,
                "PETR4",
                TransactionType.BUY,
                "stock",
                null,
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(100),
                "01/01/2024"
        );

        var position = AssetPosition.builder()
                .wallet(wallet)
                .asset(asset)
                .quantity(BigDecimal.ZERO)
                .averagePrice(BigDecimal.ZERO)
                .build();

        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        when(assetRepository.findByTicker("PETR4")).thenReturn(Optional.of(asset));
        when(positionRepository.findByWalletAndAsset(wallet, asset))
                .thenReturn(Optional.of(position));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = transactionService.create(request);

        assertThat(result.getType()).isEqualTo(TransactionType.BUY);
        assertThat(result.getAsset()).isSameAs(asset);
        assertThat(position.getQuantity()).isEqualByComparingTo("10");

        verify(assetRepository, never()).save(any(Asset.class));
        verify(positionRepository).save(position);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void should_round_price_and_quantity_before_updating_position_and_saving_transaction() {
        var position = AssetPosition.builder()
                .wallet(wallet)
                .asset(asset)
                .quantity(BigDecimal.ZERO)
                .averagePrice(BigDecimal.ZERO)
                .build();
        var tickerInfo = new AssetTickerResult("PETR4", "PETR4", false, "PETR4", "PETR4",
                "stock", "stock", "B3", "BRL", null, null, true, new BigDecimal("40.123456785"));
        var request = new CreateTransactionRequest(1L, "PETR4", TransactionType.BUY, "stock", null,
                new BigDecimal("1.1234567890125"), new BigDecimal("100.123456785"), "01/01/2024");

        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        when(assetRepository.findByTicker("PETR4")).thenReturn(Optional.of(asset));
        when(positionRepository.findByWalletAndAsset(wallet, asset)).thenReturn(Optional.of(position));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Transaction saved = transactionService.create(request, tickerInfo);

        assertThat(position.getQuantity()).isEqualByComparingTo("1.123456789013");
        assertThat(saved.getQuantity()).isEqualByComparingTo("1.123456789013");
        assertThat(saved.getPrice()).isEqualByComparingTo("100.12345679");
        assertThat(asset.getCurrentPrice()).isEqualByComparingTo("40.12345679");
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void should_reject_values_rounded_down_to_zero() {
        var request = new CreateTransactionRequest(1L, "PETR4", TransactionType.BUY, "stock", null,
                new BigDecimal("0.0000000000004"), BigDecimal.TEN, "01/01/2024");
        var tickerInfo = new AssetTickerResult("PETR4", "PETR4", false, "PETR4", "PETR4",
                "stock", "stock", "B3", "BRL", null, null, true, null);

        assertThatThrownBy(() -> transactionService.create(request, tickerInfo))
                .isInstanceOf(com.brunoSantos.wallet_app.transaction.exception.TransactionException.class)
                .hasMessageContaining("após o arredondamento");
        verifyNoInteractions(walletRepository, assetRepository, positionRepository, transactionRepository);
    }

    @Test
    void should_create_asset_if_not_exists() {

        var request = new CreateTransactionRequest(
                1L,
                "VALE3",
                TransactionType.BUY,
                "stock",
                null,
                BigDecimal.valueOf(5),
                BigDecimal.valueOf(50),
                "01/01/2024"
        );

        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        when(assetRepository.findByTicker("VALE3")).thenReturn(Optional.empty());
        when(assetRepository.save(any(Asset.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(positionRepository.findByWalletAndAsset(any(), any()))
                .thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = transactionService.create(request);

        assertThat(result.getAsset().getTicker()).isEqualTo("VALE3");
        assertThat(result.getAsset().getCurrentPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(assetRepository).save(result.getAsset());
        verify(positionRepository).save(argThat(position ->
                position.getAsset() == result.getAsset()
                        && position.getQuantity().compareTo(BigDecimal.valueOf(5)) == 0));
        verify(transactionRepository).save(result);
    }

    @Test
    void should_update_existing_asset_quote_without_creating_asset_again() {
        var request = new CreateTransactionRequest(
                1L,
                "PETR4",
                TransactionType.BUY,
                "stock",
                new BigDecimal("38.75"),
                BigDecimal.ONE,
                BigDecimal.TEN,
                "01/01/2024"
        );
        var previousUpdate = LocalDateTime.of(2024, 1, 1, 0, 0);
        asset.setLastUpdate(previousUpdate);
        when(assetProvider.validate("PETR4")).thenReturn(new AssetTickerResult(
                "PETR4", "PETR4", false, "Petrobras", "Petrobras", "stock", "stock",
                "B3", "BRL", null, null, true, new BigDecimal("38.75")));

        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        when(assetRepository.findByTicker("PETR4")).thenReturn(Optional.of(asset));
        when(positionRepository.findByWalletAndAsset(wallet, asset)).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = transactionService.create(request);

        assertThat(result.getAsset()).isSameAs(asset);
        assertThat(asset.getCurrentPrice()).isEqualByComparingTo("38.75");
        assertThat(asset.getLastUpdate()).isAfter(previousUpdate);
        verify(assetRepository, never()).save(any(Asset.class));
        verify(transactionRepository).save(result);
    }

    @Test
    void should_reject_null_transaction_type_without_treating_it_as_sell() {
        var request = new CreateTransactionRequest(1L, "PETR4", null, "stock", null,
                BigDecimal.ONE, BigDecimal.TEN, "01/01/2024");

        assertThatThrownBy(() -> transactionService.create(request))
                .isInstanceOf(com.brunoSantos.wallet_app.transaction.exception.TransactionException.class)
                .hasMessageContaining("Tipo de transação");
        verifyNoInteractions(walletRepository, assetRepository, positionRepository, transactionRepository);
    }

    @Test
    void should_reject_impossible_calendar_date_before_persisting_transaction() {
        var request = new CreateTransactionRequest(1L, "PETR4", TransactionType.BUY, "stock", null,
                BigDecimal.ONE, BigDecimal.TEN, "31/02/2024");
        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> transactionService.create(request))
                .isInstanceOf(com.brunoSantos.wallet_app.transaction.exception.TransactionException.class)
                .hasMessageContaining("Data inválida");
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void should_create_position_if_not_exists() {

        var request = new CreateTransactionRequest(
                1L,
                "PETR4",
                TransactionType.BUY,
                "stock",
                null,
                BigDecimal.valueOf(5),
                BigDecimal.valueOf(50),
                "01/01/2024"
        );

        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        when(assetRepository.findByTicker("PETR4")).thenReturn(Optional.of(asset));
        when(positionRepository.findByWalletAndAsset(wallet, asset))
                .thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.create(request);

        verify(positionRepository).save(any(AssetPosition.class));
    }

    @Test
    void should_throw_exception_when_wallet_not_found() {

        var request = new CreateTransactionRequest(
                99L,
                "PETR4",
                TransactionType.BUY,
                "stock",
                null,
                BigDecimal.ONE,
                BigDecimal.TEN,
                "01/01/2024"
        );

        when(walletRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                transactionService.create(request)
        ).isInstanceOf(WalletNotFoundException.class);
    }

    @Test
    void should_process_sell_transaction() {

        var request = new CreateTransactionRequest(
                1L,
                "PETR4",
                TransactionType.SELL,
                "stock",
                null,
                BigDecimal.valueOf(5),
                BigDecimal.valueOf(100),
                "01/01/2024"
        );

        var position = AssetPosition.builder()
                .wallet(wallet)
                .asset(asset)
                .quantity(BigDecimal.valueOf(10))
                .averagePrice(BigDecimal.valueOf(100))
                .build();

        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        when(assetRepository.findByTicker("PETR4")).thenReturn(Optional.of(asset));
        when(positionRepository.findByWalletAndAsset(wallet, asset))
                .thenReturn(Optional.of(position));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.create(request);

        assertThat(position.getQuantity()).isEqualByComparingTo("5");

        verify(assetRepository, never()).save(any(Asset.class));
        verify(positionRepository).save(position);
    }
}
