package com.brunoSantos.wallet_app.imports.controller;

import com.brunoSantos.wallet_app.asset.provider.AssetProvider;
import com.brunoSantos.wallet_app.asset.provider.AssetTickerResult;
import com.brunoSantos.wallet_app.imports.configuration.ImportExecutorConfiguration;
import com.brunoSantos.wallet_app.imports.service.ImportService;
import com.brunoSantos.wallet_app.shared.exception.GlobalExceptionHandler;
import com.brunoSantos.wallet_app.transaction.service.TransactionService;
import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.wallet.repository.WalletRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real multipart servlet parsing on a random local port, with no datasource. */
@SpringBootTest(classes = ImportUploadLimitsTest.WebConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.address=127.0.0.1", "spring.main.banner-mode=off"})
class ImportUploadLimitsTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class})
    @Import({ImportController.class, ImportService.class, ImportExecutorConfiguration.class, GlobalExceptionHandler.class})
    static class WebConfiguration {
        @Bean WalletRepository walletRepository() { return mock(WalletRepository.class); }
        @Bean TransactionService transactionService() { return mock(TransactionService.class); }
        @Bean AssetProvider assetProvider() { return mock(AssetProvider.class); }
    }

    @LocalServerPort private int port;
    @Autowired private WalletRepository wallets;
    @Autowired private TransactionService transactions;
    @Autowired private AssetProvider provider;

    @BeforeEach
    void set_up() {
        reset(wallets, transactions, provider);
        when(wallets.findById(1L)).thenReturn(Optional.of(Wallet.builder().id(1L).name("Synthetic R4 HTTP").build()));
        when(provider.validate("PETR4")).thenReturn(new AssetTickerResult("PETR4", "PETR4", false, "PETR4", "PETR4",
                "stock", "stock", "B3", "BRL", null, null, true, null));
    }

    private byte[] workbook() throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Synthetic");
            String[][] data = {{"Data", "Tipo", "", "", "", "Ticker", "Quantidade", "Preço"},
                    {"01/01/2024", "Compra", "", "", "", "PETR4", "1", "10"},
                    {"01/01/2024", "Compra", "", "", "", "PETR4", "0", "10"}};
            for (int i = 0; i < data.length; i++) {
                var row = sheet.createRow(i);
                for (int j = 0; j < data[i].length; j++) row.createCell(j).setCellValue(data[i][j]);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private HttpResponse<String> upload(byte[] file, byte[] secondFile) throws Exception {
        String boundary = "r4-synthetic-boundary";
        var body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"synthetic.xlsx\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(file);
        if (secondFile != null) {
            body.write(("\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"extra\"; filename=\"extra.xlsx\"\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            body.write(secondFile);
        }
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofByteArray(body.toByteArray());
        // Chunked transfer exercises the actual byte limit, not just Content-Length.
        // Tomcat can reject a large declared length before reading and close the
        // connection under its bounded swallow policy; do not disable that safety.
        if (secondFile != null) publisher = HttpRequest.BodyPublishers.fromPublisher(publisher);
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/wallet/1/import"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofSeconds(30)).expectContinue(true)
                    .POST(publisher).build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void returns_real_partial_result_through_http_upload() throws Exception {
        var response = upload(workbook(), null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"totalRows\":2", "\"successCount\":1", "\"errorCount\":1", "\"rowNumber\":3");
        verify(transactions).createImported(any(), any());
    }

    @Test
    void rejects_oversized_file_in_servlet_before_service() throws Exception {
        var response = upload(new byte[10 * 1024 * 1024 + 1], null);
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("10 MiB");
        verifyNoInteractions(wallets, provider, transactions);
    }

    @Test
    void rejects_request_over_eleven_mib_even_when_each_part_is_under_ten() throws Exception {
        var response = upload(new byte[6 * 1024 * 1024], new byte[6 * 1024 * 1024]);
        assertThat(response.statusCode()).isEqualTo(413);
        verifyNoInteractions(wallets, provider, transactions);
    }

    @Test
    void invalid_content_is_bad_request_without_financial_operations() throws Exception {
        var response = upload("not an Excel file".getBytes(StandardCharsets.UTF_8), null);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Conteúdo incompatível");
        verifyNoInteractions(provider, transactions);
    }

    @Test
    void rejects_third_active_upload_with_503_and_waits_for_the_first_two() throws Exception {
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        when(provider.validate("PETR4")).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("test did not release uploads");
            return new AssetTickerResult("PETR4", "PETR4", false, "PETR4", "PETR4", "stock", "stock", "B3", "BRL", null, null, true, null);
        });
        var bytes = workbook();
        try (var callers = Executors.newFixedThreadPool(2)) {
            var first = callers.submit(() -> upload(bytes, null));
            var second = callers.submit(() -> upload(bytes, null));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(upload(bytes, null).statusCode()).isEqualTo(503);
                assertThat(first.isDone()).isFalse();
                assertThat(second.isDone()).isFalse();
            } finally { release.countDown(); }
            assertThat(first.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
            assertThat(second.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        }
        verify(transactions, times(2)).createImported(any(), any());
    }
}
