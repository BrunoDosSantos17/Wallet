package com.brunoSantos.wallet_app.imports.controller;

import com.brunoSantos.wallet_app.imports.dto.ImportResponse;
import com.brunoSantos.wallet_app.imports.service.ImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
@Tag(name = "Importação", description = "Importação de operações de uma carteira")
public class ImportController {

    private final ImportService importService;

    @PostMapping("/{walletId}/import")
    @Operation(summary = "Importar transações de arquivo XLSX", description = "Envia uma planilha XLSX para importar transações na carteira informada.")
    public ResponseEntity<ImportResponse> importTransactions(
            @PathVariable Long walletId,
            @RequestParam("file") MultipartFile file) throws IOException {
        ImportResponse response = importService.importTransactions(walletId, file);
        return ResponseEntity.ok(response);
    }
}
