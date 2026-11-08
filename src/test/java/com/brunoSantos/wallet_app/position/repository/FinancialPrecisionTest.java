package com.brunoSantos.wallet_app.position.repository;

import com.brunoSantos.wallet_app.asset.domain.Asset;
import com.brunoSantos.wallet_app.asset.repository.AssetRepository;
import com.brunoSantos.wallet_app.position.domain.AssetPosition;
import com.brunoSantos.wallet_app.transaction.domain.Transaction;
import com.brunoSantos.wallet_app.transaction.domain.TransactionType;
import com.brunoSantos.wallet_app.transaction.repository.TransactionRepository;
import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.wallet.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariables({
        @EnabledIfEnvironmentVariable(named = "C3_TEST_DB_URL", matches = "jdbc:postgresql:.*"),
        @EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
})
class FinancialPrecisionTest {

    @DynamicPropertySource
    static void configureIsolatedPostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("C3_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("C3_TEST_DB_USER", "proposaldb"));
        registry.add("spring.datasource.password", () -> System.getenv("DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("brapi.token", () -> "test-token-not-used");
    }

    @Autowired private WalletRepository walletRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void postgres_round_trip_preserves_precision_for_prices_average_and_fractional_quantities() {
        Wallet wallet = walletRepository.saveAndFlush(Wallet.builder().name("C5 " + UUID.randomUUID()).build());
        Asset asset = assetRepository.saveAndFlush(Asset.builder()
                .ticker("C5" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase())
                .name("C5 precision test")
                .currentPrice(new BigDecimal("123.12345678"))
                .build());
        AssetPosition position = AssetPosition.builder()
                .wallet(wallet)
                .asset(asset)
                .quantity(new BigDecimal("1.123456789012"))
                .averagePrice(new BigDecimal("10.12345678"))
                .build();

        transactionTemplate.executeWithoutResult(status -> {
            entityManager.persist(position);
            entityManager.persist(Transaction.builder()
                    .wallet(wallet)
                    .asset(asset)
                    .type(TransactionType.BUY)
                    .quantity(new BigDecimal("0.000000000123"))
                    .price(new BigDecimal("20.12345678"))
                    .date(LocalDate.now())
                    .dataTransaction(LocalDate.now())
                    .build());
            entityManager.flush();
            entityManager.clear();

            Asset reloadedAsset = assetRepository.findById(asset.getTicker()).orElseThrow();
            AssetPosition reloadedPosition = entityManager.createQuery(
                            "select p from AssetPosition p where p.wallet.id = :walletId and p.asset.ticker = :ticker",
                            AssetPosition.class)
                    .setParameter("walletId", wallet.getId())
                    .setParameter("ticker", asset.getTicker())
                    .getSingleResult();
            Transaction reloadedTransaction = transactionRepository.findAll().stream()
                    .filter(tx -> tx.getWallet().getId().equals(wallet.getId()))
                    .findFirst().orElseThrow();

            assertThat(reloadedAsset.getCurrentPrice()).isEqualByComparingTo("123.12345678");
            assertThat(reloadedAsset.getCurrentPrice().scale()).isEqualTo(8);
            assertThat(reloadedPosition.getAveragePrice()).isEqualByComparingTo("10.12345678");
            assertThat(reloadedPosition.getAveragePrice().scale()).isEqualTo(8);
            assertThat(reloadedPosition.getQuantity()).isEqualByComparingTo("1.123456789012");
            assertThat(reloadedPosition.getQuantity().scale()).isEqualTo(12);
            assertThat(reloadedTransaction.getPrice()).isEqualByComparingTo("20.12345678");
            assertThat(reloadedTransaction.getPrice().scale()).isEqualTo(8);
            assertThat(reloadedTransaction.getQuantity()).isEqualByComparingTo("0.000000000123");
            assertThat(reloadedTransaction.getQuantity().scale()).isEqualTo(12);

            reloadedPosition.buy(new BigDecimal("0.000000000001"), new BigDecimal("20.12345678"));
            entityManager.flush();
            entityManager.clear();
            AssetPosition afterBuy = entityManager.createQuery(
                            "select p from AssetPosition p where p.wallet.id = :walletId and p.asset.ticker = :ticker",
                            AssetPosition.class)
                    .setParameter("walletId", wallet.getId())
                    .setParameter("ticker", asset.getTicker())
                    .getSingleResult();
            assertThat(afterBuy.getQuantity()).isEqualByComparingTo("1.123456789013");
            assertThat(afterBuy.getAveragePrice().scale()).isEqualTo(8);

            afterBuy.sell(new BigDecimal("0.000000000001"));
            entityManager.flush();
            entityManager.clear();
            AssetPosition afterSell = entityManager.createQuery(
                            "select p from AssetPosition p where p.wallet.id = :walletId and p.asset.ticker = :ticker",
                            AssetPosition.class)
                    .setParameter("walletId", wallet.getId())
                    .setParameter("ticker", asset.getTicker())
                    .getSingleResult();
            assertThat(afterSell.getQuantity()).isEqualByComparingTo("1.123456789012");
        });
    }
}
