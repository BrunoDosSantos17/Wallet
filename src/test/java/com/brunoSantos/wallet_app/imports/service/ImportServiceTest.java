package com.brunoSantos.wallet_app.imports.service;

import com.brunoSantos.wallet_app.asset.provider.AssetProvider;
import com.brunoSantos.wallet_app.asset.provider.AssetTickerResult;
import com.brunoSantos.wallet_app.imports.dto.ImportResponse;
import com.brunoSantos.wallet_app.shared.exception.TicketNotFoundException;
import com.brunoSantos.wallet_app.transaction.domain.Transaction;
import com.brunoSantos.wallet_app.transaction.domain.TransactionType;
import com.brunoSantos.wallet_app.transaction.dto.CreateTransactionRequest;
import com.brunoSantos.wallet_app.transaction.service.TransactionService;
import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.wallet.exception.WalletNotFoundException;
import com.brunoSantos.wallet_app.wallet.repository.WalletRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ImportServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionService transactionService;

    @Mock
    private AssetProvider assetProvider;

    @InjectMocks
    private ImportService importService;

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = Wallet.builder()
                .id(1L)
                .name("Carteira Teste")
                .build();
    }

    private AssetTickerResult createTickerResult(String symbol, String assetType, String subType) {
        return new AssetTickerResult(
                symbol,
                symbol,
                false,
                null,
                null,
                assetType,
                subType,
                "B3",
                "BRL",
                null,
                null,
                true
        );
    }

    private MockMultipartFile createXlsxFile(String[][] data) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            for (int i = 0; i < data.length; i++) {
                Row row = sheet.createRow(i);
                for (int j = 0; j < data[i].length; j++) {
                    row.createCell(j).setCellValue(data[i][j]);
                }
            }
            workbook.write(out);
            return new MockMultipartFile(
                    "file",
                    "negociacao.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray()
            );
        }
    }

    @Test
    void should_import_single_buy_transaction() throws IOException {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(assetProvider.validate(eq("PETR4")))
                .thenReturn(createTickerResult("PETR4", "stock", "stock"));
        when(transactionService.create(any(CreateTransactionRequest.class)))
                .thenReturn(Transaction.builder()
                        .id(1L)
                        .wallet(wallet)
                        .type(TransactionType.BUY)
                        .quantity(BigDecimal.valueOf(100))
                        .price(BigDecimal.valueOf(25.50))
                        .date(LocalDate.now())
                        .dataTransaction(LocalDate.of(2024, 1, 1))
                        .build());

        String[][] data = {
                {"Data", "Tipo", "", "", "", "Ticker", "Quantidade", "Preço"},
                {"01/01/2024", "Compra", "", "", "", "PETR4", "100", "25.50"}
        };

        MockMultipartFile file = createXlsxFile(data);
        ImportResponse response = importService.importTransactions(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.totalRows()).isEqualTo(1);
        assertThat(response.successCount()).isEqualTo(1);
        assertThat(response.errorCount()).isEqualTo(0);
    }

    @Test
    void should_import_multiple_transactions() throws IOException {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(assetProvider.validate(eq("PETR4")))
                .thenReturn(createTickerResult("PETR4", "stock", "stock"));
        when(assetProvider.validate(eq("VALE3")))
                .thenReturn(createTickerResult("VALE3", "stock", "stock"));
        when(transactionService.create(any(CreateTransactionRequest.class)))
                .thenReturn(Transaction.builder()
                        .id(1L)
                        .wallet(wallet)
                        .type(TransactionType.BUY)
                        .quantity(BigDecimal.valueOf(100))
                        .price(BigDecimal.valueOf(25.50))
                        .date(LocalDate.now())
                        .dataTransaction(LocalDate.of(2024, 1, 1))
                        .build());

        String[][] data = {
                {"Data", "Tipo", "", "", "", "Ticker", "Quantidade", "Preço"},
                {"01/01/2024", "Compra", "", "", "", "PETR4", "100", "25.50"},
                {"02/01/2024", "Compra", "", "", "", "VALE3", "50", "70.00"},
                {"03/01/2024", "Venda", "", "", "", "PETR4", "20", "26.00"}
        };

        MockMultipartFile file = createXlsxFile(data);
        ImportResponse response = importService.importTransactions(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.totalRows()).isEqualTo(3);
        assertThat(response.successCount()).isEqualTo(3);
        assertThat(response.errorCount()).isEqualTo(0);
    }

    @Test
    void should_map_compra_to_buy_type() throws IOException {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(assetProvider.validate(eq("PETR4")))
                .thenReturn(createTickerResult("PETR4", "stock", "stock"));
        when(transactionService.create(any(CreateTransactionRequest.class)))
                .thenAnswer(invocation -> {
                    CreateTransactionRequest request = invocation.getArgument(0);
                    assertThat(request.type()).isEqualTo(TransactionType.BUY);
                    return Transaction.builder()
                            .id(1L)
                            .wallet(wallet)
                            .type(TransactionType.BUY)
                            .quantity(BigDecimal.valueOf(100))
                            .price(BigDecimal.valueOf(25.50))
                            .date(LocalDate.now())
                            .dataTransaction(LocalDate.of(2024, 1, 1))
                            .build();
                });

        String[][] data = {
                {"Data", "Tipo", "", "", "", "Ticker", "Quantidade", "Preço"},
                {"01/01/2024", "Compra", "", "", "", "PETR4", "100", "25.50"}
        };

        MockMultipartFile file = createXlsxFile(data);
        ImportResponse response = importService.importTransactions(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.successCount()).isEqualTo(1);
    }

    @Test
    void should_map_venda_to_sell_type() throws IOException {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(assetProvider.validate(eq("PETR4")))
                .thenReturn(createTickerResult("PETR4", "stock", "stock"));
        when(transactionService.create(any(CreateTransactionRequest.class)))
                .thenAnswer(invocation -> {
                    CreateTransactionRequest request = invocation.getArgument(0);
                    assertThat(request.type()).isEqualTo(TransactionType.SELL);
                    return Transaction.builder()
                            .id(1L)
                            .wallet(wallet)
                            .type(TransactionType.SELL)
                            .quantity(BigDecimal.valueOf(100))
                            .price(BigDecimal.valueOf(25.50))
                            .date(LocalDate.now())
                            .dataTransaction(LocalDate.of(2024, 1, 1))
                            .build();
                });

        String[][] data = {
                {"Data", "Tipo", "", "", "", "Ticker", "Quantidade", "Preço"},
                {"01/01/2024", "Venda", "", "", "", "PETR4", "100", "25.50"}
        };

        MockMultipartFile file = createXlsxFile(data);
        ImportResponse response = importService.importTransactions(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.successCount()).isEqualTo(1);
    }

    @Test
    void should_normalize_ticker_to_uppercase() throws IOException {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(assetProvider.validate(eq("petr4")))
                .thenReturn(createTickerResult("PETR4", "stock", "stock"));
        when(transactionService.create(any(CreateTransactionRequest.class)))
                .thenAnswer(invocation -> {
                    CreateTransactionRequest request = invocation.getArgument(0);
                    assertThat(request.ticker()).isEqualTo("PETR4");
                    return Transaction.builder()
                            .id(1L)
                            .wallet(wallet)
                            .type(TransactionType.BUY)
                            .quantity(BigDecimal.valueOf(100))
                            .price(BigDecimal.valueOf(25.50))
                            .date(LocalDate.now())
                            .dataTransaction(LocalDate.of(2024, 1, 1))
                            .build();
                });

        String[][] data = {
                {"Data", "Tipo", "", "", "", "Ticker", "Quantidade", "Preço"},
                {"01/01/2024", "Compra", "", "", "", "petr4", "100", "25.50"}
        };

        MockMultipartFile file = createXlsxFile(data);
        ImportResponse response = importService.importTransactions(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.successCount()).isEqualTo(1);
    }

    @Test
    void should_throw_when_wallet_not_found() throws IOException {
        when(walletRepository.findById(99L)).thenReturn(Optional.empty());

        String[][] data = {
                {"Data", "Tipo", "", "", "", "Ticker", "Quantidade", "Preço"},
                {"01/01/2024", "Compra", "", "", "", "PETR4", "100", "25.50"}
        };

        MockMultipartFile file = createXlsxFile(data);

        assertThatThrownBy(() -> importService.importTransactions(99L, file))
                .isInstanceOf(WalletNotFoundException.class);
    }

    @Test
    void should_return_error_when_ticker_not_found() throws IOException {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(assetProvider.validate(eq("INVALID")))
                .thenThrow(new TicketNotFoundException(404, "Ticker não localizado: INVALID"));

        String[][] data = {
                {"Data", "Tipo", "", "", "", "Ticker", "Quantidade", "Preço"},
                {"01/01/2024", "Compra", "", "", "", "INVALID", "100", "25.50"}
        };

        MockMultipartFile file = createXlsxFile(data);
        ImportResponse response = importService.importTransactions(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.totalRows()).isEqualTo(1);
        assertThat(response.successCount()).isEqualTo(0);
        assertThat(response.errorCount()).isEqualTo(1);
        assertThat(response.errors()).isNotEmpty();
        assertThat(response.errors().get(0).message()).contains("Ticker não localizado: INVALID");
    }

    @Test
    void should_skip_rows_with_invalid_date() throws IOException {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(assetProvider.validate(eq("PETR4")))
                .thenReturn(createTickerResult("PETR4", "stock", "stock"));

        String[][] data = {
                {"Data", "Tipo", "", "", "", "Ticker", "Quantidade", "Preço"},
                {"data-invalida", "Compra", "", "", "", "PETR4", "100", "25.50"}
        };

        MockMultipartFile file = createXlsxFile(data);
        ImportResponse response = importService.importTransactions(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.errorCount()).isEqualTo(1);
        assertThat(response.errors()).isNotEmpty();
    }

    @Test
    void should_skip_rows_with_zero_quantity() throws IOException {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));

        String[][] data = {
                {"Data", "Tipo", "", "", "", "Ticker", "Quantidade", "Preço"},
                {"01/01/2024", "Compra", "", "", "", "PETR4", "0", "25.50"}
        };

        MockMultipartFile file = createXlsxFile(data);
        ImportResponse response = importService.importTransactions(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.errorCount()).isEqualTo(1);
        assertThat(response.errors()).isNotEmpty();
    }

    @Test
    void should_skip_rows_with_empty_ticker() throws IOException {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));

        String[][] data = {
                {"Data", "Tipo", "", "", "", "Ticker", "Quantidade", "Preço"},
                {"01/01/2024", "Compra", "", "", "", "", "100", "25.50"}
        };

        MockMultipartFile file = createXlsxFile(data);
        ImportResponse response = importService.importTransactions(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.errorCount()).isEqualTo(1);
        assertThat(response.errors()).isNotEmpty();
    }

    @Test
    void should_return_errors_without_stopping_import() throws IOException {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(assetProvider.validate(eq("VALE3")))
                .thenReturn(createTickerResult("VALE3", "stock", "stock"));
        when(assetProvider.validate(eq("INVALID")))
                .thenThrow(new TicketNotFoundException(404, "Ticker não localizado: INVALID"));
        when(transactionService.create(any(CreateTransactionRequest.class)))
                .thenReturn(Transaction.builder()
                        .id(1L)
                        .wallet(wallet)
                        .type(TransactionType.BUY)
                        .quantity(BigDecimal.valueOf(50))
                        .price(BigDecimal.valueOf(70.00))
                        .date(LocalDate.now())
                        .dataTransaction(LocalDate.of(2024, 1, 2))
                        .build());

        String[][] data = {
                {"Data", "Tipo", "", "", "", "Ticker", "Quantidade", "Preço"},
                {"01/01/2024", "Compra", "", "", "", "INVALID", "100", "25.50"},
                {"02/01/2024", "Compra", "", "", "", "VALE3", "50", "70.00"},
                {"data-invalida", "Venda", "", "", "", "PETR4", "20", "26.00"}
        };

        MockMultipartFile file = createXlsxFile(data);
        ImportResponse response = importService.importTransactions(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.totalRows()).isEqualTo(3);
        assertThat(response.successCount()).isEqualTo(1);
        assertThat(response.errorCount()).isEqualTo(2);
        assertThat(response.errors()).hasSize(2);
    }

    @Test
    void should_handle_fiis_tickers() throws IOException {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(assetProvider.validate(eq("HGLG11")))
                .thenReturn(createTickerResult("HGLG11", "fund", "fii"));
        when(assetProvider.validate(eq("VISC11")))
                .thenReturn(createTickerResult("VISC11", "fund", "fii"));
        when(assetProvider.validate(eq("MXRF11")))
                .thenReturn(createTickerResult("MXRF11", "fund", "fii"));
        when(transactionService.create(any(CreateTransactionRequest.class)))
                .thenReturn(Transaction.builder()
                        .id(1L)
                        .wallet(wallet)
                        .type(TransactionType.BUY)
                        .quantity(BigDecimal.TEN)
                        .price(BigDecimal.valueOf(150.00))
                        .date(LocalDate.now())
                        .dataTransaction(LocalDate.of(2024, 1, 1))
                        .build());

        String[][] data = {
                {"Data", "Tipo", "", "", "", "Ticker", "Quantidade", "Preço"},
                {"01/01/2024", "Compra", "", "", "", "HGLG11", "10", "150.00"},
                {"02/01/2024", "Compra", "", "", "", "VISC11", "5", "100.00"},
                {"03/01/2024", "Compra", "", "", "", "MXRF11", "20", "10.50"}
        };

        MockMultipartFile file = createXlsxFile(data);
        ImportResponse response = importService.importTransactions(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.totalRows()).isEqualTo(3);
        assertThat(response.successCount()).isEqualTo(3);
        assertThat(response.errorCount()).isEqualTo(0);
    }

    @Test
    void should_handle_fractional_market_tickers() throws IOException {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(assetProvider.validate(eq("BBAS3F")))
                .thenReturn(createTickerResult("BBAS3F", "stock", "stock"));
        when(assetProvider.validate(eq("ABEV3F")))
                .thenReturn(createTickerResult("ABEV3F", "stock", "stock"));
        when(transactionService.create(any(CreateTransactionRequest.class)))
                .thenReturn(Transaction.builder()
                        .id(1L)
                        .wallet(wallet)
                        .type(TransactionType.BUY)
                        .quantity(BigDecimal.valueOf(100))
                        .price(BigDecimal.valueOf(25.50))
                        .date(LocalDate.now())
                        .dataTransaction(LocalDate.of(2024, 1, 1))
                        .build());

        String[][] data = {
                {"Data", "Tipo", "", "", "", "Ticker", "Quantidade", "Preço"},
                {"01/01/2024", "Compra", "", "", "", "BBAS3F", "100", "25.50"},
                {"02/01/2024", "Compra", "", "", "", "ABEV3F", "50", "15.00"}
        };

        MockMultipartFile file = createXlsxFile(data);
        ImportResponse response = importService.importTransactions(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.totalRows()).isEqualTo(2);
        assertThat(response.successCount()).isEqualTo(2);
        assertThat(response.errorCount()).isEqualTo(0);
    }

    @Test
    void should_pass_correct_date_format_to_transaction_service() throws IOException {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(assetProvider.validate(eq("PETR4")))
                .thenReturn(createTickerResult("PETR4", "stock", "stock"));
        when(transactionService.create(any(CreateTransactionRequest.class)))
                .thenAnswer(invocation -> {
                    CreateTransactionRequest request = invocation.getArgument(0);
                    assertThat(request.date()).isEqualTo("15/03/2024");
                    return Transaction.builder()
                            .id(1L)
                            .wallet(wallet)
                            .type(TransactionType.BUY)
                            .quantity(BigDecimal.valueOf(100))
                            .price(BigDecimal.valueOf(25.50))
                            .date(LocalDate.now())
                            .dataTransaction(LocalDate.of(2024, 3, 15))
                            .build();
                });

        String[][] data = {
                {"Data", "Tipo", "", "", "", "Ticker", "Quantidade", "Preço"},
                {"15/03/2024", "Compra", "", "", "", "PETR4", "100", "25.50"}
        };

        MockMultipartFile file = createXlsxFile(data);
        ImportResponse response = importService.importTransactions(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.successCount()).isEqualTo(1);
    }
}
