package com.brunoSantos.wallet_app.imports.service;

import com.brunoSantos.wallet_app.imports.dto.ImportErrorDetail;
import com.brunoSantos.wallet_app.imports.dto.ImportResponse;
import com.brunoSantos.wallet_app.transaction.domain.TransactionType;
import com.brunoSantos.wallet_app.transaction.dto.CreateTransactionRequest;
import com.brunoSantos.wallet_app.transaction.service.TransactionService;
import com.brunoSantos.wallet_app.wallet.exception.WalletNotFoundException;
import com.brunoSantos.wallet_app.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImportService {

    private final WalletRepository walletRepository;
    private final TransactionService transactionService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int DATA_INDEX = 0;
    private static final int TICKER_INDEX = 2;
    private static final int QUANTITY_INDEX = 3;
    private static final int PRICE_INDEX = 4;
    private static final int TYPE_INDEX = 6;

    public ImportResponse importTransactions(Long walletId, MultipartFile file) throws IOException {
        var wallet = walletRepository.findById(walletId)
                .orElseThrow(WalletNotFoundException::new);

        List<ImportErrorDetail> errors = new ArrayList<>();
        int totalRows = 0;
        int successCount = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                if (line.trim().isEmpty()) {
                    continue;
                }

                totalRows++;

                try {
                    validateAndImport(line, walletId);
                    successCount++;
                } catch (Exception e) {
                    errors.add(new ImportErrorDetail(totalRows, e.getMessage()));
                }
            }
        }

        return new ImportResponse(totalRows, successCount, errors.size(), errors);
    }

    private void validateAndImport(String line, Long walletId) {
        String[] parts = line.split(",");

        String dateStr = parts[DATA_INDEX].trim();
        String ticker = parts[TICKER_INDEX].trim();
        String quantityStr = parts[QUANTITY_INDEX].trim();
        String priceStr = parts[PRICE_INDEX].trim();
        String typeStr = parts[TYPE_INDEX].trim();

        validateNotEmpty(ticker, "Ticker não pode ser vazio");

        BigDecimal quantity = getCellNumericValue(quantityStr);
        BigDecimal price = getCellNumericValue(priceStr);

        validatePositive(quantity, "Quantidade deve ser maior que zero");
        validatePositive(price, "Preço deve ser maior que zero");

        LocalDate date = parseDate(dateStr);
        TransactionType transactionType = mapTransactionType(typeStr);

        CreateTransactionRequest request = new CreateTransactionRequest(
                walletId,
                ticker.toUpperCase(),
                transactionType,
                quantity,
                price,
                date.format(DATE_FORMATTER)
        );

        transactionService.create(request);
    }

    public TransactionType mapTransactionType(String tipo) {
        if ("Compra".equalsIgnoreCase(tipo)) {
            return TransactionType.BUY;
        } else if ("Venda".equalsIgnoreCase(tipo)) {
            return TransactionType.SELL;
        }
        throw new IllegalArgumentException("Tipo de movimentação inválido: " + tipo);
    }

    public LocalDate parseDate(String dateStr) {
        validateNotEmpty(dateStr, "Data não pode ser vazia");
        try {
            return LocalDate.parse(dateStr.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data inválida: " + dateStr + ". Use o formato dd/MM/yyyy");
        }
    }

    public String getCellStringValue(String cell) {
        return cell != null ? cell.trim() : "";
    }

    public BigDecimal getCellNumericValue(String cell) {
        if (cell == null || cell.trim().isEmpty()) {
            throw new IllegalArgumentException("Valor numérico não pode ser vazio");
        }
        try {
            return new BigDecimal(cell.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor numérico inválido: " + cell);
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
