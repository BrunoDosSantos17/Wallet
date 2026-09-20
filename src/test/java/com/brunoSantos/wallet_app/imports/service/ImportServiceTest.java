package com.brunoSantos.wallet_app.imports.service;

import com.brunoSantos.wallet_app.imports.dto.ImportResponse;
import com.brunoSantos.wallet_app.transaction.domain.Transaction;
import com.brunoSantos.wallet_app.transaction.domain.TransactionType;
import com.brunoSantos.wallet_app.transaction.dto.CreateTransactionRequest;
import com.brunoSantos.wallet_app.transaction.service.TransactionService;
import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.wallet.exception.WalletNotFoundException;
import com.brunoSantos.wallet_app.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ImportServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionService transactionService;

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

    @Test
    void should_import_single_buy_transaction() throws IOException {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
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

        String csvContent = "Data,Número da Nota,Ticker,Cção,Preço unitário,Custo Total,Tipo de movimentação,Instituição,Fees,Custo Médio,Quantidade,Saldo, Mercado\n" +
                "01/01/2024,12345,PETR4,100,25.50,2550.00,Compra,Corretora,0,25.50,100,100, Brasil\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "negociacao.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                csvContent.getBytes()
        );

        ImportResponse response = importService.importTransactions(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.totalRows()).isEqualTo(1);
        assertThat(response.successCount()).isEqualTo(1);
        assertThat(response.errorCount()).isEqualTo(0);
    }

    @Test
    void should_import_multiple_transactions() throws IOException {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
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

        String csvContent = "Data,Número da Nota,Ticker,Cção,Preço unitário,Custo Total,Tipo de movimentação,Instituição,Fees,Custo Médio,Quantidade,Saldo, Mercado\n" +
                "01/01/2024,12345,PETR4,100,25.50,2550.00,Compra,Corretora,0,25.50,100,100, Brasil\n" +
                "02/01/2024,12346,VALE3,50,70.00,3500.00,Compra,Corretora,0,70.00,50,50, Brasil\n" +
                "03/01/2024,12347,PETR4,20,26.00,520.00,Venda,Corretora,0,25.50,80,80, Brasil\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "negociacao.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                csvContent.getBytes()
        );

        ImportResponse response = importService.importTransactions(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.totalRows()).isEqualTo(3);
        assertThat(response.successCount()).isEqualTo(3);
        assertThat(response.errorCount()).isEqualTo(0);
    }

    @Test
    void should_map_compra_to_buy_type() throws IOException {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
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

        String csvContent = "Data,Número da Nota,Ticker,Cção,Preço unitário,Custo Total,Tipo de movimentação,Instituição,Fees,Custo Médio,Quantidade,Saldo, Mercado\n" +
                "01/01/2024,12345,PETR4,100,25.50,2550.00,Compra,Corretora,0,25.50,100,100, Brasil\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "negociacao.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                csvContent.getBytes()
        );

        ImportResponse response = importService.importTransactions(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.successCount()).isEqualTo(1);
    }

    @Test
    void should_map_venda_to_sell_type() throws IOException {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
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

        String csvContent = "Data,Número da Nota,Ticker,Cção,Preço unitário,Custo Total,Tipo de movimentação,Instituição,Fees,Custo Médio,Quantidade,Saldo, Mercado\n" +
                "01/01/2024,12345,PETR4,100,25.50,2550.00,Venda,Corretora,0,25.50,100,100, Brasil\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "negociacao.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                csvContent.getBytes()
        );

        ImportResponse response = importService.importTransactions(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.successCount()).isEqualTo(1);
    }

    @Test
    void should_normalize_ticker_to_uppercase() throws IOException {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
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

        String csvContent = "Data,Número da Nota,Ticker,Cção,Preço unitário,Custo Total,Tipo de movimentação,Instituição,Fees,Custo Médio,Quantidade,Saldo, Mercado\n" +
                "01/01/2024,12345,petr4,100,25.50,2550.00,Compra,Corretora,0,25.50,100,100, Brasil\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "negociacao.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                csvContent.getBytes()
        );

        ImportResponse response = importService.importTransactions(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.successCount()).isEqualTo(1);
    }

    @Test
    void should_throw_when_wallet_not_found() throws IOException {
        when(walletRepository.findById(99L)).thenReturn(Optional.empty());

        String csvContent = "Data,Número da Nota,Ticker,Cção,Preço unitário,Custo Total,Tipo de movimentação,Instituição,Fees,Custo Médio,Quantidade,Saldo, Mercado\n" +
                "01/01/2024,12345,PETR4,100,25.50,2550.00,Compra,Corretora,0,25.50,100,100, Brasil\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "negociacao.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                csvContent.getBytes()
        );

        assertThatThrownBy(() -> importService.importTransactions(99L, file))
                .isInstanceOf(WalletNotFoundException.class);
    }

    @Test
    void should_skip_rows_with_invalid_date() throws IOException {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));

        String csvContent = "Data,Número da Nota,Ticker,Cção,Preço unitário,Custo Total,Tipo de movimentação,Instituição,Fees,Custo Médio,Quantidade,Saldo, Mercado\n" +
                "data-invalida,12345,PETR4,100,25.50,2550.00,Compra,Corretora,0,25.50,100,100, Brasil\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "negociacao.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                csvContent.getBytes()
        );

        ImportResponse response = importService.importTransactions(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.errorCount()).isEqualTo(1);
        assertThat(response.errors()).isNotEmpty();
    }

    @Test
    void should_skip_rows_with_zero_quantity() throws IOException {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));

        String csvContent = "Data,Número da Nota,Ticker,Cção,Preço unitário,Custo Total,Tipo de movimentação,Instituição,Fees,Custo Médio,Quantidade,Saldo, Mercado\n" +
                "01/01/2024,12345,PETR4,0,25.50,0.00,Compra,Corretora,0,25.50,0,0, Brasil\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "negociacao.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                csvContent.getBytes()
        );

        ImportResponse response = importService.importTransactions(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.errorCount()).isEqualTo(1);
        assertThat(response.errors()).isNotEmpty();
    }

    @Test
    void should_skip_rows_with_empty_ticker() throws IOException {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));

        String csvContent = "Data,Número da Nota,Ticker,Cção,Preço unitário,Custo Total,Tipo de movimentação,Instituição,Fees,Custo Médio,Quantidade,Saldo, Mercado\n" +
                "01/01/2024,12345,,100,25.50,2550.00,Compra,Corretora,0,25.50,100,100, Brasil\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "negociacao.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                csvContent.getBytes()
        );

        ImportResponse response = importService.importTransactions(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.errorCount()).isEqualTo(1);
        assertThat(response.errors()).isNotEmpty();
    }

    @Test
    void should_return_errors_without_stopping_import() throws IOException {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
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

        String csvContent = "Data,Número da Nota,Ticker,Cção,Preço unitário,Custo Total,Tipo de movimentação,Instituição,Fees,Custo Médio,Quantidade,Saldo, Mercado\n" +
                "01/01/2024,12345,,100,25.50,2550.00,Compra,Corretora,0,25.50,100,100, Brasil\n" +
                "02/01/2024,12346,VALE3,50,70.00,3500.00,Compra,Corretora,0,70.00,50,50, Brasil\n" +
                "data-invalida,12347,PETR4,20,26.00,520.00,Venda,Corretora,0,25.50,80,80, Brasil\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "negociacao.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                csvContent.getBytes()
        );

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

        String csvContent = "Data,Número da Nota,Ticker,Cção,Preço unitário,Custo Total,Tipo de movimentação,Instituição,Fees,Custo Médio,Quantidade,Saldo, Mercado\n" +
                "01/01/2024,12345,HGLG11,10,150.00,1500.00,Compra,Corretora,0,150.00,10,10, Brasil\n" +
                "02/01/2024,12346,VISC11,5,100.00,500.00,Compra,Corretora,0,100.00,5,5, Brasil\n" +
                "03/01/2024,12347,MXRF11,20,10.50,210.00,Compra,Corretora,0,10.50,20,20, Brasil\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "negociacao.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                csvContent.getBytes()
        );

        ImportResponse response = importService.importTransactions(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.totalRows()).isEqualTo(3);
        assertThat(response.successCount()).isEqualTo(3);
        assertThat(response.errorCount()).isEqualTo(0);
    }

    @Test
    void should_handle_fractional_market_tickers() throws IOException {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
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

        String csvContent = "Data,Número da Nota,Ticker,Cção,Preço unitário,Custo Total,Tipo de movimentação,Instituição,Fees,Custo Médio,Quantidade,Saldo, Mercado\n" +
                "01/01/2024,12345,BBAS3F,100,25.50,2550.00,Compra,Corretora,0,25.50,100,100, Brasil\n" +
                "02/01/2024,12346,ABEV3F,50,15.00,750.00,Compra,Corretora,0,15.00,50,50, Brasil\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "negociacao.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                csvContent.getBytes()
        );

        ImportResponse response = importService.importTransactions(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.totalRows()).isEqualTo(2);
        assertThat(response.successCount()).isEqualTo(2);
        assertThat(response.errorCount()).isEqualTo(0);
    }

    @Test
    void should_pass_correct_date_format_to_transaction_service() throws IOException {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
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

        String csvContent = "Data,Número da Nota,Ticker,Cção,Preço unitário,Custo Total,Tipo de movimentação,Instituição,Fees,Custo Médio,Quantidade,Saldo, Mercado\n" +
                "15/03/2024,12345,PETR4,100,25.50,2550.00,Compra,Corretora,0,25.50,100,100, Brasil\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "negociacao.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                csvContent.getBytes()
        );

        ImportResponse response = importService.importTransactions(1L, file);

        assertThat(response).isNotNull();
        assertThat(response.successCount()).isEqualTo(1);
    }
}
