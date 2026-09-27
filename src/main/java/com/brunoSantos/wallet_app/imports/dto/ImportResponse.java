package com.brunoSantos.wallet_app.imports.dto;

import java.util.List;

public record ImportResponse(
    int totalRows,
    int successCount,
    int errorCount,
    List<ImportErrorDetail> errors
) {
}
