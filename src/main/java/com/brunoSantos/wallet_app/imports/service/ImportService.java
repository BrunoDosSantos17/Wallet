package com.brunoSantos.wallet_app.imports.service;

import com.brunoSantos.wallet_app.asset.provider.AssetProvider;
import com.brunoSantos.wallet_app.asset.provider.AssetTickerResult;
import com.brunoSantos.wallet_app.imports.dto.ImportErrorDetail;
import com.brunoSantos.wallet_app.imports.dto.ImportResponse;
import com.brunoSantos.wallet_app.transaction.domain.TransactionType;
import com.brunoSantos.wallet_app.transaction.dto.CreateTransactionRequest;
import com.brunoSantos.wallet_app.transaction.service.TransactionService;
import com.brunoSantos.wallet_app.wallet.exception.WalletNotFoundException;
import com.brunoSantos.wallet_app.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
public class ImportService {

    private final WalletRepository walletRepository;
    private final TransactionService transactionService;
    private final AssetProvider assetProvider;
    private final Executor importExecutor;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int DATA_INDEX = 0;
    private static final int TYPE_INDEX = 1;
    private static final int TICKER_INDEX = 5;
    private static final int QUANTITY_INDEX = 6;
    private static final int PRICE_INDEX = 7;

    public ImportResponse importTransactions(Long walletId, MultipartFile file) throws IOException {
        var wallet = walletRepository.findById(walletId)
                .orElseThrow(WalletNotFoundException::new);

        List<ImportErrorDetail> errors = new ArrayList<>();
        Map<String, List<ImportRow>> rowsByTicker = new LinkedHashMap<>();
        int totalRows = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            // Assume que a primeira linha (índice 0) é o cabeçalho
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
        }

        List<CompletableFuture<GroupResult>> tasks = rowsByTicker.values().stream()
                .map(rows -> CompletableFuture.supplyAsync(() -> importTickerRows(rows, walletId), importExecutor))
                .toList();

        int successCount = 0;
        for (CompletableFuture<GroupResult> task : tasks) {
            GroupResult result = task.join();
            successCount += result.successCount();
            errors.addAll(result.errors());
        }

        errors.sort(Comparator.comparingInt(ImportErrorDetail::rowNumber));
        return new ImportResponse(totalRows, successCount, errors.size(), errors);
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) {
            return true;
        }
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK
                    && !getCellStringValue(cell).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private ImportRow extractRow(int rowNumber, Row row) {
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

        for (ImportRow row : rows) {
            try {
                validateAndImport(row, walletId);
                successCount++;
            } catch (Exception e) {
                errors.add(new ImportErrorDetail(row.rowNumber(), e.getMessage()));
            }
        }

        return new GroupResult(successCount, errors);
    }

    private void validateAndImport(ImportRow row, Long walletId) {
        String dateStr = row.date();
        String ticker = row.ticker();
        BigDecimal quantity = row.quantity();
        BigDecimal price = row.price();
        String typeStr = row.type();

        validateNotEmpty(ticker, "Ticker não pode ser vazio");
        validatePositive(quantity, "Quantidade deve ser maior que zero");
        validatePositive(price, "Preço deve ser maior que zero");

        AssetTickerResult tickerResult = assetProvider.validate(ticker);

        LocalDate date = parseDate(dateStr, null);
        TransactionType transactionType = mapTransactionType(typeStr);

        CreateTransactionRequest request = new CreateTransactionRequest(
                walletId,
                ticker.toUpperCase(),
                transactionType,
                tickerResult.assetType(),
                tickerResult.lastPrice(),
                quantity,
                price,
                date.format(DATE_FORMATTER)
        );

        transactionService.create(request);
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
            case FORMULA -> cell.getCellFormula();
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
