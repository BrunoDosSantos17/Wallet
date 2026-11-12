package com.brunoSantos.wallet_app.dividend.service;

import com.brunoSantos.wallet_app.asset.domain.AssetType;
import com.brunoSantos.wallet_app.asset.repository.AssetRepository;
import com.brunoSantos.wallet_app.dividend.repository.DividendoRecebidoRepository;
import com.brunoSantos.wallet_app.integration.smarterdividends.client.SmarterDividendsClient;
import com.brunoSantos.wallet_app.position.repository.AssetPositionRepository;
import com.brunoSantos.wallet_app.transaction.domain.TransactionType;
import com.brunoSantos.wallet_app.transaction.dto.CreateTransactionRequest;
import com.brunoSantos.wallet_app.transaction.service.TransactionService;
import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariables({
        @EnabledIfEnvironmentVariable(named = "C3_TEST_DB_URL", matches = "jdbc:postgresql:.*"),
        @EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+"),
        @EnabledIfEnvironmentVariable(named = "C8_LIVE_JNJ", matches = "true")
})
class JnjLivePostgresTest {
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", com.brunoSantos.wallet_app.shared.testing.IsolatedPostgres::url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("C3_TEST_DB_USER", "proposaldb"));
        registry.add("spring.datasource.password", () -> System.getenv("DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("brapi.token", () -> "not-used");
    }

    @Autowired SmarterDividendsClient client;
    @Autowired WalletRepository wallets;
    @Autowired AssetRepository assets;
    @Autowired TransactionService transactions;
    @Autowired AssetPositionRepository positions;
    @Autowired DividendSyncService sync;
    @Autowired DividendoRecebidoRepository dividends;

    @Test
    @Transactional
    void registers_fictional_jnj_purchase_and_syncs_latest_live_payment() {
        var profile = client.profile("JNJ");
        var payment = profile.dividend().lastPayment();
        var calendar = client.calendar(payment.exDate());
        var date = LocalDate.parse(calendar.lastDayToBuy()).minusDays(1);
        var wallet = wallets.saveAndFlush(Wallet.builder().name("JNJ isolated " + UUID.randomUUID()).build());
        transactions.create(new CreateTransactionRequest(wallet.getId(), "JNJ", TransactionType.BUY, "stock_abroad", null,
                BigDecimal.TEN, new BigDecimal("100"), date.format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))));
        var asset = assets.findById("JNJ").orElseThrow();
        assertThat(asset.getType()).isEqualTo(AssetType.STOCK_ABROAD);
        var position = positions.findByWalletAndAsset(wallet, asset).orElseThrow();
        var result = sync.sync(position.getId(), LocalDate.parse(payment.payDate()));
        assertThat(result.inseridos()).isEqualTo(1);
        var month = java.time.YearMonth.from(LocalDate.parse(payment.payDate()));
        assertThat(dividends.sumByWalletAndPeriodAndCurrency(wallet.getId(), month.atDay(1), month.plusMonths(1).atDay(1), "USD"))
                .isEqualByComparingTo(payment.amount().multiply(BigDecimal.TEN));
    }
}
