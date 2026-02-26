package com.brunoSantos.transaction;

import com.brunoSantos.transaction.dto.CreateTransactionRequest;
import com.brunoSantos.transaction.dto.TransactionResponse;
import com.brunoSantos.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public TransactionResponse create(@RequestBody @Valid CreateTransactionRequest request) {

        var transaction = transactionService.create(request);

        return TransactionResponse.fromEntity(transaction);
    }
}
