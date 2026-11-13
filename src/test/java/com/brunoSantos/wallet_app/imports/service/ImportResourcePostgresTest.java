package com.brunoSantos.wallet_app.imports.service;

import com.brunoSantos.wallet_app.asset.provider.AssetProvider;
import com.brunoSantos.wallet_app.asset.provider.AssetTickerResult;
import com.brunoSantos.wallet_app.asset.repository.AssetRepository;
import com.brunoSantos.wallet_app.position.repository.AssetPositionRepository;
import com.brunoSantos.wallet_app.shared.testing.IsolatedPostgres;
import com.brunoSantos.wallet_app.transaction.repository.TransactionRepository;
import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.wallet.repository.WalletRepository;
import com.brunoSantos.wallet_app.wallet.service.WalletService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Synthetic C4 regression: real commits and positions, no personal XLSX needed. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariables({
        @EnabledIfEnvironmentVariable(named = "C3_TEST_DB_URL", matches = "jdbc:postgresql:.*"),
        @EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")})
class ImportResourcePostgresTest {
    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", IsolatedPostgres::url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("C3_TEST_DB_USER", "wallet_test"));
        registry.add("spring.datasource.password", () -> System.getenv("DB_PASSWORD"));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("brapi.token", () -> "synthetic-not-used");
    }

    @Autowired private ImportService imports;
    @Autowired private WalletRepository wallets;
    @Autowired private WalletService walletService;
    @Autowired private TransactionRepository transactions;
    @Autowired private AssetPositionRepository positions;
    @Autowired private AssetRepository assets;
    @MockitoBean private AssetProvider provider;

    @Test
    void provider_failure_and_failed_sale_preserve_commits_and_resubmission_does_not_duplicate() throws Exception {
        var wallet = wallets.saveAndFlush(Wallet.builder().name("R4 synthetic " + UUID.randomUUID()).build());
        String symbol = "R4" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        String unavailable = "FAIL" + symbol;
        when(provider.validate(anyString())).thenAnswer(call -> {
            String ticker = call.getArgument(0);
            if (ticker.equals(unavailable)) throw new IllegalStateException("synthetic provider timeout");
            return new AssetTickerResult(ticker, ticker, false, ticker, ticker, "stock", "stock", "B3", "BRL", null, null, true, null);
        });
        try {
            byte[] bytes;
            try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
                var sheet = workbook.createSheet("Synthetic");
                String[][] data = {{"Data", "Tipo", "", "", "", "Ticker", "Quantidade", "Preço"},
                        {"01/01/2024", "Compra", "", "", "", symbol, "1", "10"},
                        {"01/01/2024", "Venda", "", "", "", symbol, "2", "10"},
                        {"01/01/2024", "Compra", "", "", "", symbol, "2", "10"},
                        {"01/01/2024", "Compra", "", "", "", unavailable, "1", "10"}};
                for (int i = 0; i < data.length; i++) {
                    var row = sheet.createRow(i);
                    for (int j = 0; j < data[i].length; j++) row.createCell(j).setCellValue(data[i][j]);
                }
                workbook.write(output);
                bytes = output.toByteArray();
            }
            var file = new MockMultipartFile("file", "synthetic.xlsx", "application/octet-stream", bytes);
            var first = imports.importTransactions(wallet.getId(), file);
            assertThat(first.successCount()).isEqualTo(2);
            assertThat(first.errors()).extracting(error -> error.rowNumber()).containsExactly(3, 5);
            assertThat(positions.findAllByWallet(wallet).getFirst().getQuantity()).isEqualByComparingTo("3");
            assertThat(transactions.findAll()).filteredOn(t -> t.getWallet().getId().equals(wallet.getId())).hasSize(2);
            var second = imports.importTransactions(wallet.getId(), file);
            assertThat(second.successCount()).isEqualTo(1);
            assertThat(second.errorCount()).isEqualTo(3);
            assertThat(positions.findAllByWallet(wallet).getFirst().getQuantity()).isEqualByComparingTo("1");
            assertThat(transactions.findAll()).filteredOn(t -> t.getWallet().getId().equals(wallet.getId())).hasSize(3);
            verify(provider, times(2)).validate(symbol);
            verify(provider, times(4)).validate(unavailable);
        } finally {
            walletService.delete(wallet.getId());
            assets.deleteById(symbol);
        }
    }
}
