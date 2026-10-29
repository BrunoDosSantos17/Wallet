package com.brunoSantos.wallet_app.transaction.controller;

import com.brunoSantos.wallet_app.transaction.dto.CreateTransactionRequest;
import com.brunoSantos.wallet_app.transaction.dto.TransactionResponse;
import com.brunoSantos.wallet_app.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
@Tag(name = "Transações", description = "Registro de operações de ativos")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @Operation(summary = "Registrar transação")
    public TransactionResponse create(@RequestBody @Valid CreateTransactionRequest request) {

        var transaction = transactionService.create(request);

        return TransactionResponse.fromEntity(transaction);
    }
}
