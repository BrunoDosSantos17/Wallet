package com.brunoSantos.wallet_app.imports.service;

import com.brunoSantos.wallet_app.asset.provider.AssetProvider;
import com.brunoSantos.wallet_app.asset.provider.AssetTickerResult;
import com.brunoSantos.wallet_app.shared.exception.ImportException;
import com.brunoSantos.wallet_app.transaction.exception.DuplicateImportException;
import com.brunoSantos.wallet_app.transaction.service.TransactionService;
import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.wallet.repository.WalletRepository;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipInputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImportResourceLimitsTest {
    private final WalletRepository wallets = mock(WalletRepository.class);
    private final TransactionService transactions = mock(TransactionService.class);
    private final AssetProvider provider = mock(AssetProvider.class);
    private ImportService service;

    @BeforeEach
    void set_up() {
        when(wallets.findById(1L)).thenReturn(Optional.of(Wallet.builder().id(1L).name("Synthetic R4").build()));
        service = new ImportService(wallets, transactions, provider, Runnable::run);
    }

    private AssetTickerResult ticker(String symbol) {
        return new AssetTickerResult(symbol, symbol, false, symbol, symbol,
                "stock", "stock", "B3", "BRL", null, null, true, null);
    }

    private void header(Sheet sheet) {
        var header = sheet.createRow(0);
        String[] names = {"Data do Negócio", "Tipo de Movimentação", "", "", "", "Código de Negociação", "Quantidade", "Preço"};
        for (int i = 0; i < names.length; i++) header.createCell(i).setCellValue(names[i]);
    }

    private Row row(Sheet sheet, int index, String symbol) {
        var row = sheet.createRow(index);
        row.createCell(0).setCellValue("01/01/2024");
        row.createCell(1).setCellValue("Compra");
        row.createCell(5).setCellValue(symbol);
        row.createCell(6).setCellValue("1");
        row.createCell(7).setCellValue("10");
        return row;
    }

    private MockMultipartFile file(boolean xlsx, Consumer<Workbook> customize) throws Exception {
        try (Workbook workbook = xlsx ? new XSSFWorkbook() : new HSSFWorkbook();
             var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Negociação");
            header(sheet);
            row(sheet, 1, "PETR4");
            customize.accept(workbook);
            workbook.write(output);
            return new MockMultipartFile("file", xlsx ? "synthetic.xlsx" : "synthetic.xls", "application/octet-stream", output.toByteArray());
        }
    }

    private void assert_preflight_accepts(MockMultipartFile file) throws Exception {
        var path = ImportWorkbookValidator.prepare(file);
        try { assertThat(Files.size(path)).isEqualTo(file.getBytes().length); }
        finally { Files.delete(path); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void accepts_xls_and_xlsx_with_native_date_and_local_numbers(boolean xlsx) throws Exception {
        when(provider.validate("PETR4")).thenReturn(ticker("PETR4"));
        var file = file(xlsx, workbook -> {
            var row = workbook.getSheetAt(0).getRow(1);
            var date = row.getCell(0);
            date.setCellValue(java.time.LocalDate.of(2024, 1, 1));
            var style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("dd/MM/yyyy"));
            date.setCellStyle(style);
            row.getCell(6).setCellValue("1.234,56");
            row.getCell(7).setCellValue("25,50");
        });
        var result = service.importTransactions(1L, file);
        assertThat(result.successCount()).isEqualTo(1);
        verify(transactions).createImported(argThat(request -> request.quantity().compareTo(new BigDecimal("1234.56")) == 0
                && request.price().compareTo(new BigDecimal("25.50")) == 0 && request.date().equals("01/01/2024")), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void accepts_exact_row_and_column_boundaries_before_opening_workbook(boolean xlsx) throws Exception {
        var file = file(xlsx, workbook -> {
            var sheet = workbook.getSheetAt(0);
            for (int i = 2; i <= 5000; i++) row(sheet, i, "PETR4");
            sheet.getRow(5000).createCell(19).setCellValue("last allowed column");
        });
        assert_preflight_accepts(file);
        when(provider.validate("PETR4")).thenReturn(ticker("PETR4"));
        var result = service.importTransactions(1L, file);
        assertThat(result.totalRows()).isEqualTo(5000);
        assertThat(result.successCount()).isEqualTo(5000);
        verify(provider).validate("PETR4");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejects_excess_rows_columns_and_sheets_before_workbook_creation(boolean xlsx) throws Exception {
        for (Consumer<Workbook> modification : java.util.List.<Consumer<Workbook>>of(
                workbook -> row(workbook.getSheetAt(0), 5001, "PETR4"),
                workbook -> workbook.getSheetAt(0).getRow(1).createCell(20).setCellValue("excess"),
                workbook -> workbook.createSheet("extra"))) {
            var file = file(xlsx, modification);
            assertThatThrownBy(() -> ImportWorkbookValidator.prepare(file)).isInstanceOf(ImportException.class);
            assertThatThrownBy(() -> service.importTransactions(1L, file)).isInstanceOf(ImportException.class);
        }
        verifyNoInteractions(transactions, provider);
    }

    @Test
    void rejects_missing_header_invalid_header_empty_sheet_and_header_only_sheet() throws Exception {
        for (Consumer<Workbook> modification : java.util.List.<Consumer<Workbook>>of(
                workbook -> workbook.getSheetAt(0).removeRow(workbook.getSheetAt(0).getRow(0)),
                workbook -> workbook.getSheetAt(0).getRow(0).getCell(5).setCellValue("wrong"),
                workbook -> workbook.getSheetAt(0).removeRow(workbook.getSheetAt(0).getRow(1)),
                workbook -> { workbook.getSheetAt(0).removeRow(workbook.getSheetAt(0).getRow(0));
                    workbook.getSheetAt(0).removeRow(workbook.getSheetAt(0).getRow(1)); },
                workbook -> workbook.removeSheetAt(0),
                workbook -> workbook.getSheetAt(0).getRow(0).getCell(0).setCellFormula("1+1"))) {
            var file = file(true, modification);
            assertThatThrownBy(() -> service.importTransactions(1L, file)).isInstanceOf(ImportException.class);
        }
        verifyNoInteractions(transactions, provider);
    }

    @Test
    void rejects_empty_wrong_extension_mismatched_content_and_corrupt_zip() throws Exception {
        var valid = file(true, workbook -> {});
        for (var invalid : java.util.List.of(
                new MockMultipartFile("file", "empty.xlsx", "", new byte[0]),
                new MockMultipartFile("file", "file.txt", "", valid.getBytes()),
                new MockMultipartFile("file", "file.xls", "", valid.getBytes()),
                new MockMultipartFile("file", "file.xlsx", "", "not excel".getBytes()),
                new MockMultipartFile("file", "file.xlsx", "", new byte[]{'P', 'K', 3, 4, 0, 0, 0, 0}))) {
            assertThatThrownBy(() -> service.importTransactions(1L, invalid)).isInstanceOf(ImportException.class);
        }
        verifyNoInteractions(transactions, provider);
    }

    @Test
    void enforces_size_even_when_multipart_metadata_lies() throws Exception {
        var valid = file(true, workbook -> {});
        var atLimit = new MockMultipartFile("file", "file.xlsx", "", valid.getBytes()) {
            @Override public long getSize() { return ImportWorkbookValidator.MAX_FILE_BYTES; }
        };
        assert_preflight_accepts(atLimit);
        var oversized = new MockMultipartFile("file", "file.xlsx", "", valid.getBytes()) {
            @Override public long getSize() { return ImportWorkbookValidator.MAX_FILE_BYTES + 1; }
            @Override public java.io.InputStream getInputStream() { throw new AssertionError("must not open oversized upload"); }
        };
        assertThatThrownBy(() -> ImportWorkbookValidator.prepare(oversized)).isInstanceOf(ImportException.class).hasMessageContaining("10 MiB");
        var dishonest = new MockMultipartFile("file", "file.xlsx", "", new byte[(int) ImportWorkbookValidator.MAX_FILE_BYTES + 1]) {
            @Override public long getSize() { return 1; }
        };
        assertThatThrownBy(() -> ImportWorkbookValidator.prepare(dishonest)).isInstanceOf(ImportException.class).hasMessageContaining("10 MiB");
    }

    @Test
    void rejects_zip_expansion_over_fifty_mib_before_opening_workbook() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("padding.bin"));
            byte[] block = new byte[8192];
            for (int i = 0; i < 6400; i++) zip.write(block);
            zip.write(1);
            zip.closeEntry();
        }
        var file = new MockMultipartFile("file", "bomb.xlsx", "", output.toByteArray());
        assertThat(file.getSize()).isLessThan(ImportWorkbookValidator.MAX_FILE_BYTES);
        assertThatThrownBy(() -> ImportWorkbookValidator.prepare(file)).isInstanceOf(ImportException.class).hasMessageContaining("50 MiB");
        verifyNoInteractions(transactions, provider);
    }

    private MockMultipartFile relocate_sheet(MockMultipartFile file) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var input = new ZipInputStream(new ByteArrayInputStream(file.getBytes())); var zip = new ZipOutputStream(output)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                var bytes = input.readAllBytes();
                String name = entry.getName();
                if (name.equals("[Content_Types].xml") || name.equals("xl/_rels/workbook.xml.rels")) {
                    bytes = new String(bytes, StandardCharsets.UTF_8)
                            .replace("/xl/worksheets/sheet1.xml", "/custom/sheet.xml")
                            .replace("worksheets/sheet1.xml", "../custom/sheet.xml").getBytes(StandardCharsets.UTF_8);
                }
                if (name.equals("xl/worksheets/sheet1.xml")) name = "custom/sheet.xml";
                zip.putNextEntry(new ZipEntry(name));
                zip.write(bytes);
                zip.closeEntry();
            }
        }
        return new MockMultipartFile("file", "relocated.xlsx", "application/octet-stream", output.toByteArray());
    }

    @Test
    void follows_real_sheet_relationships_so_nonstandard_part_names_cannot_bypass_preflight() throws Exception {
        assert_preflight_accepts(relocate_sheet(file(true, workbook -> {})));
        var oversized = relocate_sheet(file(true, workbook -> row(workbook.getSheetAt(0), 5001, "PETR4")));
        assertThatThrownBy(() -> ImportWorkbookValidator.prepare(oversized)).isInstanceOf(ImportException.class).hasMessageContaining("5000");
        verifyNoInteractions(transactions, provider);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejects_formula_rows_without_evaluation_and_preserves_valid_rows(boolean xlsx) throws Exception {
        when(provider.validate("PETR4")).thenReturn(ticker("PETR4"));
        var file = file(xlsx, workbook -> {
            var sheet = workbook.getSheetAt(0);
            sheet.getRow(1).getCell(6).setCellFormula("1+1");
            row(sheet, 2, "PETR4").createCell(18).setCellFormula("1/0");
            row(sheet, 3, "PETR4");
            row(sheet, 4, "PETR4").getCell(6).setCellValue("1.23,4");
        });
        var result = service.importTransactions(1L, file);
        assertThat(result.totalRows()).isEqualTo(4);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.errors()).extracting(error -> error.rowNumber()).containsExactly(2, 3, 5);
        assertThat(result.errors().getFirst().message()).contains("Fórmulas");
        verify(transactions).createImported(any(), any());
    }

    @Test
    void caches_success_per_normalized_ticker_only_during_upload_and_does_not_retry_duplicates() throws Exception {
        when(provider.validate(anyString())).thenAnswer(call -> ticker(call.getArgument(0)));
        when(transactions.createImported(any(), any())).thenThrow(new DuplicateImportException());
        var file = file(true, workbook -> {
            row(workbook.getSheetAt(0), 2, "petr4");
            row(workbook.getSheetAt(0), 3, " PETR4 ");
        });
        for (int i = 0; i < 2; i++) {
            var result = service.importTransactions(1L, file);
            assertThat(result.errorCount()).isEqualTo(3);
        }
        verify(provider, times(2)).validate("PETR4");
        verify(transactions, times(6)).createImported(any(), any());
    }

    @Test
    void provider_failures_are_not_cached_and_retries_remain_per_line() throws Exception {
        when(provider.validate("PETR4"))
                .thenThrow(new IllegalStateException("read timed out"))
                .thenThrow(new IllegalStateException("read timed out"))
                .thenReturn(ticker("PETR4"));
        var file = file(true, workbook -> {
            row(workbook.getSheetAt(0), 2, "PETR4");
            row(workbook.getSheetAt(0), 3, "PETR4");
        });
        var result = service.importTransactions(1L, file);
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.errorCount()).isEqualTo(1);
        verify(provider, times(3)).validate("PETR4");
        verify(transactions, times(2)).createImported(any(), any());
    }

    @Test
    void executor_rejection_before_any_task_is_503_and_releases_upload_permit() throws Exception {
        var rejecting = new ImportService(wallets, transactions, provider, command -> { throw new RejectedExecutionException(); });
        var file = file(true, workbook -> {});
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> rejecting.importTransactions(1L, file))
                    .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(503));
        }
        verifyNoInteractions(transactions, provider);
    }

    @Test
    void rejection_after_submission_waits_for_started_tasks_and_returns_errors_per_rejected_row() throws Exception {
        var accepted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        AtomicInteger submissions = new AtomicInteger();
        try (var worker = Executors.newSingleThreadExecutor(); var caller = Executors.newSingleThreadExecutor()) {
            Executor executor = command -> {
                if (submissions.incrementAndGet() > 1) throw new RejectedExecutionException();
                worker.execute(command);
            };
            var importing = new ImportService(wallets, transactions, provider, executor);
            when(provider.validate("PETR4")).thenAnswer(call -> {
                accepted.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test did not release worker");
                return ticker("PETR4");
            });
            var file = file(true, workbook -> {
                row(workbook.getSheetAt(0), 2, "VALE3");
                row(workbook.getSheetAt(0), 3, "VALE3");
            });
            var response = caller.submit(() -> importing.importTransactions(1L, file));
            try {
                assertThat(accepted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(response.isDone()).isFalse();
            } finally { release.countDown(); }
            var result = response.get(10, TimeUnit.SECONDS);
            assertThat(result.successCount()).isEqualTo(1);
            assertThat(result.errors()).extracting(error -> error.rowNumber()).containsExactly(3, 4);
            verify(transactions).createImported(any(), any());
            verify(provider, never()).validate("VALE3");
        }
    }

    @Test
    void two_active_uploads_reject_third_and_release_permits_after_completion() throws Exception {
        var active = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        when(provider.validate("PETR4")).thenAnswer(call -> {
            active.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test did not release uploads");
            return ticker("PETR4");
        });
        var file = file(true, workbook -> {});
        try (var callers = Executors.newFixedThreadPool(2)) {
            var first = callers.submit(() -> service.importTransactions(1L, file));
            var second = callers.submit(() -> service.importTransactions(1L, file));
            try {
                assertThat(active.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> service.importTransactions(1L, file))
                        .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(503));
            } finally { release.countDown(); }
            assertThat(first.get(10, TimeUnit.SECONDS).successCount()).isEqualTo(1);
            assertThat(second.get(10, TimeUnit.SECONDS).successCount()).isEqualTo(1);
        }
        assertThat(service.importTransactions(1L, file).successCount()).isEqualTo(1);
    }
}
