package com.brunoSantos.wallet_app.transaction.service;

import com.brunoSantos.wallet_app.asset.domain.Asset;
import com.brunoSantos.wallet_app.asset.repository.AssetRepository;
import com.brunoSantos.wallet_app.position.domain.AssetPosition;
import com.brunoSantos.wallet_app.position.repository.AssetPositionRepository;
import com.brunoSantos.wallet_app.transaction.domain.Transaction;
import com.brunoSantos.wallet_app.transaction.domain.TransactionType;
import com.brunoSantos.wallet_app.transaction.dto.CreateTransactionRequest;
import com.brunoSantos.wallet_app.transaction.repository.TransactionRepository;
import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

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

    @InjectMocks
    private TransactionService transactionService;

    private Wallet wallet;
    private Asset asset;

    @BeforeEach
    void setUp() {
        wallet = Wallet.builder().id(1L).name("Main").build();

        asset = Asset.builder()
                .ticker("PETR4")
                .name("PETR4")
                .currentPrice(BigDecimal.ZERO)
                .lastUpdate(LocalDateTime.now())
                .build();
    }

    @Test
    void should_create_buy_transaction_when_asset_and_position_exist() {

        var request = new CreateTransactionRequest(
                1L,
                "PETR4",
                TransactionType.BUY,
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(100)
        );

        var position = AssetPosition.builder()
                .wallet(wallet)
                .asset(asset)
                .quantity(BigDecimal.ZERO)
                .averagePrice(BigDecimal.ZERO)
                .build();

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(assetRepository.findByTicker("PETR4")).thenReturn(Optional.of(asset));
        when(positionRepository.findByWalletAndAsset(wallet, asset))
                .thenReturn(Optional.of(position));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = transactionService.create(request);

        assertThat(result.getType()).isEqualTo(TransactionType.BUY);
        assertThat(position.getQuantity()).isEqualByComparingTo("10");

        verify(positionRepository).save(position);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void should_create_asset_if_not_exists() {

        var request = new CreateTransactionRequest(
                1L,
                "VALE3",
                TransactionType.BUY,
                BigDecimal.valueOf(5),
                BigDecimal.valueOf(50)
        );

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(assetRepository.findByTicker("VALE3")).thenReturn(Optional.empty());
        when(assetRepository.save(any(Asset.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(positionRepository.findByWalletAndAsset(any(), any()))
                .thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.create(request);

        verify(assetRepository).save(any(Asset.class));
    }

    @Test
    void should_create_position_if_not_exists() {

        var request = new CreateTransactionRequest(
                1L,
                "PETR4",
                TransactionType.BUY,
                BigDecimal.valueOf(5),
                BigDecimal.valueOf(50)
        );

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
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
                BigDecimal.ONE,
                BigDecimal.TEN
        );

        when(walletRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                transactionService.create(request)
        ).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Wallet not found");
    }

    @Test
    void should_process_sell_transaction() {

        var request = new CreateTransactionRequest(
                1L,
                "PETR4",
                TransactionType.SELL,
                BigDecimal.valueOf(5),
                BigDecimal.valueOf(100)
        );

        var position = AssetPosition.builder()
                .wallet(wallet)
                .asset(asset)
                .quantity(BigDecimal.valueOf(10))
                .averagePrice(BigDecimal.valueOf(100))
                .build();

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(assetRepository.findByTicker("PETR4")).thenReturn(Optional.of(asset));
        when(positionRepository.findByWalletAndAsset(wallet, asset))
                .thenReturn(Optional.of(position));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.create(request);

        assertThat(position.getQuantity()).isEqualByComparingTo("5");

        verify(positionRepository).save(position);
    }
}