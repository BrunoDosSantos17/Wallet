package com.brunoSantos.wallet_app.imports.controller;

import com.brunoSantos.wallet_app.imports.dto.ImportResponse;
import com.brunoSantos.wallet_app.imports.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    @PostMapping("/{walletId}/import")
    public ResponseEntity<ImportResponse> importTransactions(
            @PathVariable Long walletId,
            @RequestParam("file") MultipartFile file) throws IOException {
        ImportResponse response = importService.importTransactions(walletId, file);
        return ResponseEntity.ok(response);
    }
}
