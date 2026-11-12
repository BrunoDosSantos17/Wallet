package com.brunoSantos.wallet_app.dividend.service;

import com.brunoSantos.wallet_app.asset.repository.AssetRepository;
import com.brunoSantos.wallet_app.integration.awesomeapi.client.AwesomeApiClient;
import com.brunoSantos.wallet_app.integration.awesomeapi.dto.RawCurrencyQuote;
import com.brunoSantos.wallet_app.integration.smarterdividends.client.SmarterDividendsClient;
import com.brunoSantos.wallet_app.integration.smarterdividends.dto.SmarterCalendar;
import com.brunoSantos.wallet_app.integration.smarterdividends.dto.SmarterDividendProfile;
import com.brunoSantos.wallet_app.position.repository.AssetPositionRepository;
import com.brunoSantos.wallet_app.transaction.domain.TransactionType;
import com.brunoSantos.wallet_app.transaction.dto.CreateTransactionRequest;
import com.brunoSantos.wallet_app.transaction.service.TransactionService;
import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.wallet.repository.WalletRepository;
import com.brunoSantos.wallet_app.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariables({
        @EnabledIfEnvironmentVariable(named = "C3_TEST_DB_URL", matches = "jdbc:postgresql:.*"),
        @EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
})
class JnjFlowPostgresTest {
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("C3_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("C3_TEST_DB_USER", "proposaldb"));
        registry.add("spring.datasource.password", () -> System.getenv("DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("brapi.token", () -> "not-used");
    }
    @MockitoBean SmarterDividendsClient client;
    @MockitoBean AwesomeApiClient currency;
    @Autowired WalletRepository wallets;
    @Autowired AssetRepository assets;
    @Autowired TransactionService transactions;
    @Autowired AssetPositionRepository positions;
    @Autowired DividendSyncService sync;
    @Autowired WalletService walletService;

    @Test
    @Transactional
    void stock_purchase_to_sync_and_summary_with_exchange_failure() {
        LocalDate pay = YearMonth.now(ZoneId.of("America/Sao_Paulo")).atDay(1);
        LocalDate ex = pay.minusDays(4);
        LocalDate base = ex.minusDays(1);
        var payment = new SmarterDividendProfile.Payment(ex.toString(), pay.toString(), new BigDecimal("1.34"));
        when(client.profile("JNJ")).thenReturn(new SmarterDividendProfile("JNJ", "Johnson & Johnson", "stock", "https://smarterdividends.com/stocks/JNJ",
                new SmarterDividendProfile.Dividend(payment)));
        when(client.calendar(ex.toString())).thenReturn(new SmarterCalendar(ex.toString(), base.toString(),
                List.of(new SmarterCalendar.Entry("JNJ", pay.toString(), new BigDecimal("1.34")))));
        when(currency.getRawQuote("USD-BRL"))
                .thenReturn(Map.of("USDBRL", new RawCurrencyQuote("USD", "BRL", new BigDecimal("5.1234"))))
                .thenThrow(new IllegalStateException("sem câmbio"));
        var wallet = wallets.saveAndFlush(Wallet.builder().name("JNJ fixture " + UUID.randomUUID()).build());
        transactions.create(new CreateTransactionRequest(wallet.getId(), "JNJ", TransactionType.BUY, "stock_abroad", null,
                BigDecimal.TEN, new BigDecimal("100"), base.minusDays(1).format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))));
        var position = positions.findByWalletAndAsset(wallet, assets.findById("JNJ").orElseThrow()).orElseThrow();
        assertThat(sync.sync(position.getId(), pay).inseridos()).isEqualTo(1);
        assertThat(sync.sync(position.getId(), pay).existentes()).isEqualTo(1);
        assertThat(walletService.infoBasica(wallet.getId()).rendaMensalEst()).isEqualByComparingTo("68.65");
        var unavailable = walletService.infoBasica(wallet.getId());
        assertThat(unavailable.rendaMensalEst()).isEqualByComparingTo("0.00");
        assertThat(unavailable.avisos()).containsExactly("ConversaoMoedaIndisponivel");
    }
}
