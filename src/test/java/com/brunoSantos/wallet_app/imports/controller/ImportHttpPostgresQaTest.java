package com.brunoSantos.wallet_app.imports.controller;

import com.brunoSantos.wallet_app.asset.provider.AssetProvider;
import com.brunoSantos.wallet_app.asset.provider.AssetTickerResult;
import com.brunoSantos.wallet_app.asset.repository.AssetRepository;
import com.brunoSantos.wallet_app.position.repository.AssetPositionRepository;
import com.brunoSantos.wallet_app.shared.testing.IsolatedPostgres;
import com.brunoSantos.wallet_app.transaction.repository.TransactionRepository;
import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.wallet.repository.WalletRepository;
import com.brunoSantos.wallet_app.wallet.service.WalletService;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** QA: actual servlet -> parser -> bounded executor -> transactional proxy -> disposable PostgreSQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.address=127.0.0.1", "spring.main.banner-mode=off"})
@EnabledIfEnvironmentVariables({
        @EnabledIfEnvironmentVariable(named = "C3_TEST_DB_URL", matches = "jdbc:postgresql:.*"),
        @EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")})
class ImportHttpPostgresQaTest {
    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", IsolatedPostgres::url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("C3_TEST_DB_USER", "wallet_test"));
        registry.add("spring.datasource.password", () -> System.getenv("DB_PASSWORD"));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("brapi.token", () -> "synthetic-not-used");
    }

    @LocalServerPort int port;
    @Autowired WalletRepository wallets;
    @Autowired WalletService walletService;
    @Autowired TransactionRepository transactions;
    @Autowired AssetPositionRepository positions;
    @Autowired AssetRepository assets;
    @Autowired @Qualifier("importExecutor") Executor executor;
    @MockitoBean AssetProvider provider;

    private String symbol() { return "QA" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(); }

    private AssetTickerResult active(String symbol) {
        return new AssetTickerResult(symbol, symbol, false, symbol, symbol, "stock", "stock", "B3", "BRL", null, null, true, null);
    }

    private byte[] workbook(boolean xlsx, List<String> symbols, boolean partial) throws Exception {
        try (Workbook workbook = xlsx ? new XSSFWorkbook() : new HSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Synthetic QA");
            var header = sheet.createRow(0);
            String[] labels = {"Data do Negócio", "Tipo de Movimentação", "", "", "", "Código de Negociação", "Quantidade", "Preço"};
            for (int i = 0; i < labels.length; i++) header.createCell(i).setCellValue(labels[i]);
            for (int i = 0; i < symbols.size(); i++) {
                var row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue("01/01/2024");
                row.createCell(1).setCellValue("Compra");
                row.createCell(5).setCellValue(symbols.get(i));
                row.createCell(6).setCellValue(1);
                row.createCell(7).setCellValue(10);
                if (partial && i == 0) { row.getCell(6).setCellValue("1.234,56"); row.getCell(7).setCellValue("25,50"); }
                if (partial && i == 1) row.getCell(6).setCellFormula("1+1");
                if (partial && i == 2) row.getCell(6).setCellValue(2);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private HttpResponse<String> upload(long wallet, byte[] bytes, boolean xlsx) throws Exception {
        String boundary = "qa-r4-boundary";
        var out = new ByteArrayOutputStream();
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"synthetic."
                + (xlsx ? "xlsx" : "xls") + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/wallet/" + wallet + "/import"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary).timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private void committed(Wallet wallet, int count) {
        // Repository calls run outside the HTTP transaction: these must be committed, not just flushed.
        assertThat(transactions.findAll()).filteredOn(t -> t.getWallet().getId().equals(wallet.getId())).hasSize(count);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void actual_http_formats_formulas_local_numbers_and_replay_preserve_commits(boolean xlsx) throws Exception {
        var wallet = wallets.saveAndFlush(Wallet.builder().name("QA formats " + UUID.randomUUID()).build());
        String symbol = symbol();
        when(provider.validate(anyString())).thenAnswer(call -> active(call.getArgument(0)));
        try {
            byte[] file = workbook(xlsx, List.of(symbol, symbol, symbol), true);
            var first = upload(wallet.getId(), file, xlsx);
            assertThat(first.statusCode()).isEqualTo(200);
            assertThat(first.body()).contains("\"successCount\":2", "\"errorCount\":1", "\"rowNumber\":3", "Fórmulas");
            committed(wallet, 2);
            assertThat(positions.findAllByWallet(wallet).getFirst().getQuantity()).isEqualByComparingTo("1236.56");
            var replay = upload(wallet.getId(), file, xlsx);
            assertThat(replay.statusCode()).isEqualTo(200);
            assertThat(replay.body()).contains("\"successCount\":0", "\"errorCount\":3");
            committed(wallet, 2);
            assertThat(positions.findAllByWallet(wallet).getFirst().getQuantity()).isEqualByComparingTo("1236.56");
        } finally { walletService.delete(wallet.getId()); assets.deleteById(symbol); }
    }

    @Test
    void real_saturated_executor_returns_partial_commits_and_replay_inserts_only_rejected_operation() throws Exception {
        var wallet = wallets.saveAndFlush(Wallet.builder().name("QA saturation " + UUID.randomUUID()).build());
        String prefix = symbol();
        List<String> symbols = new ArrayList<>();
        for (int i = 0; i < 105; i++) symbols.add(prefix + i);
        var release = new CountDownLatch(1);
        var active = new CountDownLatch(4);
        var rejected = new CountDownLatch(5);
        var pool = ((ThreadPoolTaskExecutor) executor).getThreadPoolExecutor();
        var original = pool.getRejectedExecutionHandler();
        pool.setRejectedExecutionHandler((task, worker) -> { rejected.countDown(); throw new RejectedExecutionException("QA observed full queue"); });
        // Fill all workers *before* submitting the workbook. Waiting on the first
        // rejection alone races with submission of later groups and cannot predict
        // which row is rejected. Here exactly 100 groups fit and the final five do not.
        for (int i = 0; i < 4; i++) pool.execute(() -> {
            active.countDown();
            try {
                if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("QA did not release workers");
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        });
        when(provider.validate(anyString())).thenAnswer(call -> active(call.getArgument(0)));
        try (var caller = Executors.newSingleThreadExecutor()) {
            byte[] file = workbook(true, symbols, false);
            assertThat(active.await(10, TimeUnit.SECONDS)).isTrue();
            var response = caller.submit(() -> upload(wallet.getId(), file, true));
            try {
                assertThat(active.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(rejected.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(pool.getQueue()).hasSize(100);
                assertThat(response.isDone()).isFalse();
                committed(wallet, 0);
            } finally { release.countDown(); }
            var first = response.get(60, TimeUnit.SECONDS);
            assertThat(first.statusCode()).isEqualTo(200);
            assertThat(first.body()).contains("\"successCount\":100", "\"errorCount\":5", "\"rowNumber\":102", "\"rowNumber\":106", "Executor ocupado");
            committed(wallet, 100);
            assertThat(positions.findAllByWallet(wallet)).hasSize(100);
            var replaySymbols = new ArrayList<String>();
            replaySymbols.add(symbols.getFirst());
            replaySymbols.addAll(symbols.subList(100, 105));
            var replayFile = workbook(true, replaySymbols, false);
            var replay = upload(wallet.getId(), replayFile, true);
            assertThat(replay.statusCode()).isEqualTo(200);
            assertThat(replay.body()).contains("\"successCount\":5", "\"errorCount\":1");
            committed(wallet, 105);
            assertThat(positions.findAllByWallet(wallet)).hasSize(105);
            var third = upload(wallet.getId(), replayFile, true);
            assertThat(third.body()).contains("\"successCount\":0", "\"errorCount\":6");
            committed(wallet, 105);
        } finally {
            release.countDown();
            pool.setRejectedExecutionHandler(original);
            walletService.delete(wallet.getId());
            for (String symbol : symbols) if (assets.existsById(symbol)) assets.deleteById(symbol);
        }
    }
}
