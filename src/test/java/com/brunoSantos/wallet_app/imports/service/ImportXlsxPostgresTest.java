package com.brunoSantos.wallet_app.imports.service;

import com.brunoSantos.wallet_app.asset.provider.AssetProvider;
import com.brunoSantos.wallet_app.asset.provider.AssetTickerResult;
import com.brunoSantos.wallet_app.position.repository.AssetPositionRepository;
import com.brunoSantos.wallet_app.transaction.repository.TransactionRepository;
import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.wallet.repository.WalletRepository;
import org.apache.poi.ss.usermodel.WorkbookFactory;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariables({
        @EnabledIfEnvironmentVariable(named = "C3_TEST_DB_URL", matches = "jdbc:postgresql:.*"),
        @EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+"),
        @EnabledIfEnvironmentVariable(named = "C4_XLSX_PATH", matches = ".+")
})
class ImportXlsxPostgresTest {
    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("C3_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("C3_TEST_DB_USER", "proposaldb"));
        registry.add("spring.datasource.password", () -> System.getenv("DB_PASSWORD"));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("brapi.token", () -> "test-token-not-used");
    }

    @Autowired private ImportService importService;
    @Autowired private WalletRepository walletRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private AssetPositionRepository positionRepository;
    @MockitoBean private AssetProvider assetProvider;

    @Test
    void imports_fixture_based_on_real_header_and_counts_only_committed_rows() throws Exception {
        var wallet = walletRepository.saveAndFlush(Wallet.builder().name("C4 " + UUID.randomUUID()).build());
        String ticker = "C4" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        when(assetProvider.validate(anyString())).thenAnswer(call -> {
            String symbol = call.getArgument(0);
            return new AssetTickerResult(symbol, symbol, false, symbol, symbol,
                    "stock", "stock", "B3", "BRL", null, null, true, null);
        });

        byte[] bytes;
        try (var input = Files.newInputStream(Path.of(System.getenv("C4_XLSX_PATH")));
             var original = WorkbookFactory.create(input);
             var fixture = new XSSFWorkbook();
             var output = new ByteArrayOutputStream()) {
            var source = original.getSheetAt(0).getRow(0);
            var sheet = fixture.createSheet("Negociação");
            var header = sheet.createRow(0);
            for (int i = 0; i < 9; i++) header.createCell(i).setCellValue(source.getCell(i).toString());
            assertThat(header.getCell(0).getStringCellValue()).containsIgnoringCase("data");
            assertThat(header.getCell(5).getStringCellValue()).containsIgnoringCase("negociação");
            String[][] rows = {
                    {"02/01/2024", "Compra", "1", "10"},
                    {"02/01/2024", "Venda", "2", "10"},
                    {"02/01/2024", "Compra", "2", "10"}
            };
            for (int i = 0; i < rows.length; i++) {
                var row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(rows[i][0]);
                row.createCell(1).setCellValue(rows[i][1]);
                row.createCell(5).setCellValue(ticker);
                row.createCell(6).setCellValue(rows[i][2]);
                row.createCell(7).setCellValue(rows[i][3]);
            }
            fixture.write(output);
            bytes = output.toByteArray();
        }
        var file = new MockMultipartFile("file", "fixture.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
        var first = importService.importTransactions(wallet.getId(), file);
        assertThat(first.totalRows()).isEqualTo(3);
        assertThat(first.successCount()).isEqualTo(2);
        assertThat(first.errorCount()).isEqualTo(1);
        assertThat(first.errors().getFirst().rowNumber()).isEqualTo(3);
        assertThat(transactionRepository.findAll()).filteredOn(t -> t.getWallet().getId().equals(wallet.getId())).hasSize(2);
        assertThat(positionRepository.findAllByWallet(wallet).getFirst().getQuantity()).isEqualByComparingTo("3");

        var second = importService.importTransactions(wallet.getId(), file);
        assertThat(second.totalRows()).isEqualTo(3);
        assertThat(second.successCount()).isEqualTo(1);
        assertThat(second.errorCount()).isEqualTo(2);
        assertThat(transactionRepository.findAll()).filteredOn(t -> t.getWallet().getId().equals(wallet.getId())).hasSize(3);
        assertThat(positionRepository.findAllByWallet(wallet).getFirst().getQuantity()).isEqualByComparingTo("1");
    }
}
