package com.brunoSantos.wallet_app.transaction.service;

import com.brunoSantos.wallet_app.asset.domain.Asset;
import com.brunoSantos.wallet_app.asset.repository.AssetRepository;
import com.brunoSantos.wallet_app.position.domain.AssetPosition;
import com.brunoSantos.wallet_app.position.repository.AssetPositionRepository;
import com.brunoSantos.wallet_app.transaction.domain.Transaction;
import com.brunoSantos.wallet_app.transaction.domain.TransactionType;
import com.brunoSantos.wallet_app.transaction.repository.TransactionRepository;
import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.wallet.repository.WalletRepository;
import com.brunoSantos.wallet_app.asset.provider.AssetTickerResult;
import com.brunoSantos.wallet_app.transaction.dto.CreateTransactionRequest;
import com.brunoSantos.wallet_app.transaction.exception.TransactionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariables({
        @EnabledIfEnvironmentVariable(named = "C3_TEST_DB_URL", matches = "jdbc:postgresql:.*"),
        @EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
})
class TransactionConcurrencyTest {

    @DynamicPropertySource
    static void configureIsolatedPostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("C3_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("C3_TEST_DB_USER", "proposaldb"));
        registry.add("spring.datasource.password", () -> System.getenv("DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("brapi.token", () -> "test-token-not-used");
    }

    @Autowired private WalletRepository walletRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AssetPositionRepository positionRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private TransactionService transactionService;

    private TransactionTemplate transactionTemplate;
    private Wallet wallet;
    private Asset asset;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        wallet = walletRepository.saveAndFlush(Wallet.builder().name("C3 test " + UUID.randomUUID()).build());
        asset = assetRepository.saveAndFlush(Asset.builder()
                .ticker("C3" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase())
                .name("C3 concurrent test")
                .currentPrice(BigDecimal.TEN)
                .build());
    }

    @Test
    void failed_sale_does_not_rollback_prior_commit_and_same_date_can_continue() {
        transactionService.createImported(importRequest("02/01/2024"), tickerInfo());
        var sell = new CreateTransactionRequest(wallet.getId(), asset.getTicker(), TransactionType.SELL,
                "stock", null, new BigDecimal("2"), BigDecimal.TEN, "02/01/2024");
        assertThatThrownBy(() -> transactionService.createImported(sell, tickerInfo())).isInstanceOf(RuntimeException.class);
        var secondBuy = new CreateTransactionRequest(wallet.getId(), asset.getTicker(), TransactionType.BUY,
                "stock", null, new BigDecimal("2"), BigDecimal.TEN, "02/01/2024");
        transactionService.createImported(secondBuy, tickerInfo());
        assertThat(positionRepository.findByWalletAndAsset(wallet, asset).orElseThrow().getQuantity())
                .isEqualByComparingTo("3");
        assertThat(transactionRepository.findAll()).filteredOn(tx -> tx.getWallet().getId().equals(wallet.getId()))
                .hasSize(2);
    }

    @Test
    void imported_replay_and_retroactive_operation_do_not_change_position() {
        var request = importRequest("02/01/2024");
        transactionService.createImported(request, tickerInfo());
        assertThatThrownBy(() -> transactionService.createImported(request, tickerInfo()))
                .isInstanceOf(TransactionException.class).hasMessageContaining("duplicada");
        assertThatThrownBy(() -> transactionService.createImported(importRequest("01/01/2024"), tickerInfo()))
                .isInstanceOf(TransactionException.class).hasMessageContaining("retroativa");
        assertThat(positionRepository.findByWalletAndAsset(wallet, asset).orElseThrow().getQuantity())
                .isEqualByComparingTo("1");
        assertThat(transactionRepository.findAll()).filteredOn(tx -> tx.getWallet().getId().equals(wallet.getId()))
                .hasSize(1);
    }

    @Test
    void concurrent_identical_imports_commit_only_once() throws Exception {
        var start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> attemptImport(start));
            var second = executor.submit(() -> attemptImport(start));
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(positionRepository.findByWalletAndAsset(wallet, asset).orElseThrow().getQuantity())
                .isEqualByComparingTo("1");
        assertThat(transactionRepository.findAll()).filteredOn(tx -> tx.getWallet().getId().equals(wallet.getId()))
                .hasSize(1);
    }

    private boolean attemptImport(CountDownLatch start) {
        await(start);
        try {
            transactionService.createImported(importRequest("02/01/2024"), tickerInfo());
            return true;
        } catch (TransactionException ex) {
            if (!ex.getMessage().contains("duplicada")) throw ex;
            return false;
        }
    }

    private CreateTransactionRequest importRequest(String date) {
        return new CreateTransactionRequest(wallet.getId(), asset.getTicker(), TransactionType.BUY,
                "stock", null, BigDecimal.ONE, BigDecimal.TEN, date);
    }

    private AssetTickerResult tickerInfo() {
        String ticker = asset.getTicker();
        return new AssetTickerResult(ticker, ticker, false, ticker, ticker,
                "stock", "stock", "B3", "BRL", null, null, true, null);
    }

    @Test
    void concurrent_buys_do_not_lose_an_update_and_loser_operation_rolls_back() throws Exception {
        AssetPosition position = positionRepository.saveAndFlush(AssetPosition.builder()
                .wallet(wallet).asset(asset).quantity(BigDecimal.TEN).averagePrice(BigDecimal.TEN).build());

        assertTwoTransactionsReadSameVersionThenCompete(position, managed -> {
            managed.buy(BigDecimal.ONE, BigDecimal.TEN);
        }, TransactionType.BUY);

        AssetPosition current = positionRepository.findByWalletAndAsset(wallet, asset).orElseThrow();
        assertThat(current.getQuantity()).isEqualByComparingTo("11");
        assertThat(transactionRepository.findAll()).filteredOn(tx -> tx.getWallet().getId().equals(wallet.getId()))
                .hasSize(1);
    }

    @Test
    void concurrent_sells_cannot_overdraw_and_loser_operation_rolls_back() throws Exception {
        AssetPosition position = positionRepository.saveAndFlush(AssetPosition.builder()
                .wallet(wallet).asset(asset).quantity(BigDecimal.TEN).averagePrice(BigDecimal.TEN).build());

        assertTwoTransactionsReadSameVersionThenCompete(position, managed -> {
            managed.sell(new BigDecimal("7"));
        }, TransactionType.SELL);

        AssetPosition current = positionRepository.findByWalletAndAsset(wallet, asset).orElseThrow();
        assertThat(current.getQuantity()).isEqualByComparingTo("3");
        assertThat(current.getQuantity()).isNotNegative();
        assertThat(transactionRepository.findAll()).filteredOn(tx -> tx.getWallet().getId().equals(wallet.getId()))
                .hasSize(1);
    }

    @Test
    void concurrent_creation_of_missing_position_is_limited_by_unique_pair_constraint() throws Exception {
        CountDownLatch bothReadMissing = new CountDownLatch(2);
        CountDownLatch allowInsert = new CountDownLatch(1);
        AtomicInteger inserted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> createMissingPosition(bothReadMissing, allowInsert, inserted, rejected));
            Future<?> second = executor.submit(() -> createMissingPosition(bothReadMissing, allowInsert, inserted, rejected));
            assertThat(bothReadMissing.await(10, TimeUnit.SECONDS)).isTrue();
            allowInsert.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        }

        assertThat(inserted).hasValue(1);
        assertThat(rejected).hasValue(1);
        assertThat(positionRepository.findAllByWallet(wallet))
                .filteredOn(position -> position.getAsset().getTicker().equals(asset.getTicker()))
                .hasSize(1);
    }

    private void assertTwoTransactionsReadSameVersionThenCompete(AssetPosition position,
                                                                  PositionMutation mutation,
                                                                  TransactionType type) throws Exception {
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch allowWrites = new CountDownLatch(1);
        AtomicInteger successful = new AtomicInteger();
        AtomicInteger conflicted = new AtomicInteger();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> updatePositionInOwnTransaction(position.getId(), type,
                    bothRead, allowWrites, successful, conflicted, mutation));
            Future<?> second = executor.submit(() -> updatePositionInOwnTransaction(position.getId(), type,
                    bothRead, allowWrites, successful, conflicted, mutation));
            assertThat(bothRead.await(10, TimeUnit.SECONDS)).isTrue();
            allowWrites.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        }

        assertThat(successful).hasValue(1);
        assertThat(conflicted).hasValue(1);
    }

    private void updatePositionInOwnTransaction(Long positionId, TransactionType type,
                                                CountDownLatch bothRead, CountDownLatch allowWrites,
                                                AtomicInteger successful, AtomicInteger conflicted,
                                                PositionMutation mutation) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                AssetPosition managed = positionRepository.findById(positionId).orElseThrow();
                bothRead.countDown();
                await(allowWrites);
                mutation.apply(managed);
                transactionRepository.saveAndFlush(transaction(type));
                positionRepository.saveAndFlush(managed);
            });
            successful.incrementAndGet();
        } catch (OptimisticLockingFailureException ex) {
            conflicted.incrementAndGet();
        }
    }

    private void createMissingPosition(CountDownLatch bothReadMissing, CountDownLatch allowInsert,
                                      AtomicInteger inserted, AtomicInteger rejected) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                assertThat(positionRepository.findByWalletAndAsset(wallet, asset)).isEmpty();
                bothReadMissing.countDown();
                await(allowInsert);
                positionRepository.saveAndFlush(AssetPosition.builder()
                        .wallet(wallet).asset(asset).quantity(BigDecimal.ONE).averagePrice(BigDecimal.TEN).build());
            });
            inserted.incrementAndGet();
        } catch (DataIntegrityViolationException ex) {
            rejected.incrementAndGet();
        }
    }

    private Transaction transaction(TransactionType type) {
        return Transaction.builder().wallet(wallet).asset(asset).type(type)
                .quantity(BigDecimal.ONE).price(BigDecimal.TEN)
                .date(LocalDate.now()).dataTransaction(LocalDate.now()).build();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for concurrent transaction");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating concurrent transactions", ex);
        }
    }

    @FunctionalInterface
    private interface PositionMutation {
        void apply(AssetPosition position);
    }
}
