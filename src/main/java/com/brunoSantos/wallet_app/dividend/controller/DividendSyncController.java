package com.brunoSantos.wallet_app.dividend.controller;

import com.brunoSantos.wallet_app.dividend.service.DividendSyncService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/dividendos")
public class DividendSyncController {
    private final DividendSyncService service;

    public DividendSyncController(DividendSyncService service) { this.service = service; }

    @PostMapping("/posicoes/{id}/sincronizar")
    public DividendSyncService.SyncResult sync(@PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio) {
        return service.sync(id, inicio);
    }
}
