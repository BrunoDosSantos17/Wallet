package com.brunoSantos.wallet_app.imports.service;

import com.brunoSantos.wallet_app.imports.dto.ImportErrorDetail;
import com.brunoSantos.wallet_app.imports.dto.ImportResponse;
import com.brunoSantos.wallet_app.transaction.domain.TransactionType;
import com.brunoSantos.wallet_app.transaction.dto.CreateTransactionRequest;
import com.brunoSantos.wallet_app.transaction.service.TransactionService;
import com.brunoSantos.wallet_app.transaction.exception.DuplicateImportException;
import com.brunoSantos.wallet_app.shared.financial.FinancialPrecision;
import com.brunoSantos.wallet_app.asset.provider.AssetProvider;
import com.brunoSantos.wallet_app.asset.provider.AssetTickerResult;
import com.brunoSantos.wallet_app.wallet.exception.WalletNotFoundException;
import com.brunoSantos.wallet_app.wallet.repository.WalletRepository;
import com.brunoSantos.wallet_app.shared.exception.ImportException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

@Service
@RequiredArgsConstructor
public class ImportService {

    private final WalletRepository walletRepository;
    private final TransactionService transactionService;
    private final AssetProvider assetProvider;
    private final Executor importExecutor;
    private final Semaphore uploadPermits = new Semaphore(2);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int DATA_INDEX = 0;
    private static final int TYPE_INDEX = 1;
    private static final int TICKER_INDEX = 5;
    private static final int QUANTITY_INDEX = 6;
    private static final int PRICE_INDEX = 7;

    public ImportResponse importTransactions(Long walletId, MultipartFile file) throws IOException {
        if (!uploadPermits.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Importações ocupadas; tente novamente");
        }
        try {
            return importWithinLimits(walletId, file);
        } finally {
            uploadPermits.release();
        }
    }

    private ImportResponse importWithinLimits(Long walletId, MultipartFile file) throws IOException {
        var wallet = walletRepository.findById(walletId)
                .orElseThrow(WalletNotFoundException::new);

        List<ImportErrorDetail> errors = new ArrayList<>();
        Map<String, List<ImportRow>> rowsByTicker = new LinkedHashMap<>();
        int totalRows = 0;

        var path = ImportWorkbookValidator.prepare(file);
        try (Workbook workbook = openWorkbook(path.toFile())) {
            ImportWorkbookValidator.validateWorkbook(workbook);
            Sheet sheet = workbook.getSheetAt(0);
            validateHeader(sheet.getRow(0));

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);

                if (isRowEmpty(row)) {
                    continue;
                }

                totalRows++;

                try {
                    ImportRow importRow = extractRow(rowIndex + 1, row);
                    String tickerKey = importRow.ticker().trim().toUpperCase(Locale.ROOT);
                    rowsByTicker.computeIfAbsent(tickerKey, ignored -> new ArrayList<>()).add(importRow);
                } catch (Exception e) {
                    errors.add(new ImportErrorDetail(rowIndex + 1, e.getMessage()));
                }
            }
        } finally {
            Files.deleteIfExists(path);
        }
        if (totalRows == 0) throw new ImportException("Planilha sem operações");

        List<CompletableFuture<GroupResult>> tasks = new ArrayList<>();
        for (var rows : rowsByTicker.values()) {
            try {
                tasks.add(CompletableFuture.supplyAsync(() -> importTickerRows(rows, walletId), importExecutor));
            } catch (RejectedExecutionException ex) {
                if (tasks.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Executor de importação ocupado; tente novamente");
                }
                for (var row : rows) {
                    errors.add(new ImportErrorDetail(row.rowNumber(), "Executor ocupado; linha não processada"));
                }
            }
        }

        int successCount = 0;
        for (CompletableFuture<GroupResult> task : tasks) {
            GroupResult result = task.join();
            successCount += result.successCount();
            errors.addAll(result.errors());
        }

        errors.sort(Comparator.comparingInt(ImportErrorDetail::rowNumber));
        return new ImportResponse(totalRows, successCount, errors.size(), errors);
    }

    private Workbook openWorkbook(java.io.File file) {
        try {
            return WorkbookFactory.create(file, null, true);
        } catch (Exception ex) {
            throw new ImportException("Arquivo XLS/XLSX inválido ou corrompido");
        }
    }

    private void validateHeader(Row header) {
        if (header == null) throw new ImportException("Cabeçalho obrigatório");
        rejectFormulas(header);
        validateHeaderCell(header, DATA_INDEX, Set.of("data", "data do negocio"));
        validateHeaderCell(header, TYPE_INDEX, Set.of("tipo", "tipo de movimentacao"));
        validateHeaderCell(header, TICKER_INDEX, Set.of("ticker", "codigo de negociacao"));
        validateHeaderCell(header, QUANTITY_INDEX, Set.of("quantidade"));
        validateHeaderCell(header, PRICE_INDEX, Set.of("preco"));
    }

    private void validateHeaderCell(Row row, int index, Set<String> accepted) {
        String label = Normalizer.normalize(getCellStringValue(row.getCell(index)), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (!accepted.contains(label)) throw new ImportException("Cabeçalho inválido na coluna " + (index + 1));
    }

    private void rejectFormulas(Row row) {
        for (var cell : row) {
            if (cell.getCellType() == CellType.FORMULA) {
                throw new ImportException("Fórmulas não são permitidas");
            }
        }
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) {
            return true;
        }
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK
                    && (cell.getCellType() != CellType.STRING || !cell.getStringCellValue().isBlank())) {
                return false;
            }
        }
        return true;
    }

    private ImportRow extractRow(int rowNumber, Row row) {
        rejectFormulas(row);
        String dateStr = getCellStringValue(row.getCell(DATA_INDEX));
        String ticker = getCellStringValue(row.getCell(TICKER_INDEX));
        BigDecimal quantity = getCellNumericValue(row.getCell(QUANTITY_INDEX));
        BigDecimal price = getCellNumericValue(row.getCell(PRICE_INDEX));
        String typeStr = getCellStringValue(row.getCell(TYPE_INDEX));

        return new ImportRow(rowNumber, dateStr, ticker, quantity, price, typeStr);
    }

    private GroupResult importTickerRows(List<ImportRow> rows, Long walletId) {
        int successCount = 0;
        List<ImportErrorDetail> errors = new ArrayList<>();
        Map<String, AssetTickerResult> validatedTickers = new HashMap<>();

        for (ImportRow row : rows) {
            try {
                validateAndImport(row, walletId, validatedTickers);
                successCount++;
            } catch (Exception e) {
                errors.add(new ImportErrorDetail(row.rowNumber(), e.getMessage()));
            }
        }

        return new GroupResult(successCount, errors);
    }

    private void validateAndImport(ImportRow row, Long walletId, Map<String, AssetTickerResult> validatedTickers) {
        String dateStr = row.date();
        String ticker = row.ticker();
        BigDecimal quantity = row.quantity();
        BigDecimal price = row.price();
        String typeStr = row.type();

        validateNotEmpty(ticker, "Ticker não pode ser vazio");
        validatePositive(quantity, "Quantidade deve ser maior que zero");
        validatePositive(price, "Preço deve ser maior que zero");

        LocalDate date = parseDate(dateStr, null);
        TransactionType transactionType = mapTransactionType(typeStr);

        // Reject inputs that cannot produce a valid persisted identity before retrying.
        BigDecimal normalizedQuantity = FinancialPrecision.normalizeQuantity(quantity);
        BigDecimal normalizedPrice = FinancialPrecision.normalizePrice(price);
        validatePositive(normalizedQuantity, "Quantidade deve permanecer maior que zero após arredondamento");
        validatePositive(normalizedPrice, "Preço deve permanecer maior que zero após arredondamento");

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                importAttempt(walletId, ticker, quantity, price, date, transactionType, validatedTickers);
                return;
            } catch (DuplicateImportException ex) {
                throw ex;
            } catch (Exception ex) {
                if (attempt == 2) throw ex;
            }
        }
    }

    private void importAttempt(Long walletId, String ticker, BigDecimal quantity, BigDecimal price,
                               LocalDate date, TransactionType transactionType,
                               Map<String, AssetTickerResult> validatedTickers) {
        String key = ticker.trim().toUpperCase(Locale.ROOT);
        AssetTickerResult tickerResult = validatedTickers.get(key);
        if (tickerResult == null) {
            tickerResult = assetProvider.validate(ticker);
            if (tickerResult != null && key.equalsIgnoreCase(tickerResult.symbol())
                    && Boolean.TRUE.equals(tickerResult.isActive())) {
                validatedTickers.put(key, tickerResult);
            }
        }

        CreateTransactionRequest request = new CreateTransactionRequest(
                walletId,
                ticker.trim().toUpperCase(Locale.ROOT),
                transactionType,
                tickerResult.assetType(),
                null,
                quantity,
                price,
                date.format(DATE_FORMATTER)
        );

        transactionService.createImported(request, tickerResult);
    }

    private record ImportRow(int rowNumber, String date, String ticker, BigDecimal quantity,
                             BigDecimal price, String type) {}

    private record GroupResult(int successCount, List<ImportErrorDetail> errors) {}

    public TransactionType mapTransactionType(String tipo) {
        if ("Compra".equalsIgnoreCase(tipo)) {
            return TransactionType.BUY;
        } else if ("Venda".equalsIgnoreCase(tipo)) {
            return TransactionType.SELL;
        }
        throw new IllegalArgumentException("Tipo de movimentação inválido: " + tipo);
    }

    /**
     * Faz o parse da data considerando que o Excel pode armazenar
     * a célula como data "nativa" (numérica) ou como texto dd/MM/yyyy.
     */
    public LocalDate parseDate(String dateStr, Cell cell) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }

        validateNotEmpty(dateStr, "Data não pode ser vazia");
        try {
            return LocalDate.parse(dateStr.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data inválida: " + dateStr + ". Use o formato dd/MM/yyyy");
        }
    }

    public String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().format(DATE_FORMATTER);
                }
                yield BigDecimal.valueOf(cell.getNumericCellValue()).toPlainString();
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> throw new ImportException("Fórmulas não são permitidas");
            default -> "";
        };
    }

    public BigDecimal getCellNumericValue(Cell cell) {
        if (cell == null) {
            throw new IllegalArgumentException("Valor numérico não pode ser vazio");
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        String raw = getCellStringValue(cell);
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("Valor numérico não pode ser vazio");
        }
        try {
            if (raw.contains(",") && raw.contains(".")) {
                if (!raw.matches("[+-]?\\d{1,3}(?:\\.\\d{3})+,\\d+")) throw new NumberFormatException();
                raw = raw.replace(".", "");
            }
            return new BigDecimal(raw.replace(",", "."));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor numérico inválido: " + raw);
        }
    }

    public void validateNotEmpty(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    public void validatePositive(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
