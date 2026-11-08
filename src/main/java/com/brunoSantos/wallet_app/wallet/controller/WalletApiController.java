package com.brunoSantos.wallet_app.wallet.controller;

import com.brunoSantos.wallet_app.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class WalletApiController {

    private final WalletService walletService;

    @GetMapping("/api/wallet/all")
    public List<String> findAllNames() {
        return walletService.findAll().stream().map(wallet -> wallet.getName()).toList();
    }
}
