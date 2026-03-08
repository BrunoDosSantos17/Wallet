package com.brunoSantos.wallet_app.wallet.controller;

import com.brunoSantos.wallet_app.wallet.dto.CreateWalletRequest;
import com.brunoSantos.wallet_app.wallet.dto.UpdateWalletRequest;
import com.brunoSantos.wallet_app.wallet.dto.WalletResponse;
import com.brunoSantos.wallet_app.wallet.exception.WalletExistsException;
import com.brunoSantos.wallet_app.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping
    public ResponseEntity<WalletResponse> create(@RequestBody @Valid CreateWalletRequest request) {

        var wallet = walletService.createWallet(request.name());

        return ResponseEntity.ok(
                WalletResponse.fromEntity(wallet)
        );
    }

    @GetMapping("/{id}")
    public WalletResponse findById(@PathVariable Long id) {
        var wallet = walletService.findById(id);
        return WalletResponse.fromEntity(wallet);
    }

    @GetMapping
    public List<WalletResponse> findAll() {
        return walletService.findAll()
                .stream()
                .map(WalletResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @PutMapping
    public WalletResponse renameWallet(@RequestBody UpdateWalletRequest updateWalletRequest) {
        return WalletResponse.fromEntity(walletService.update(updateWalletRequest));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        walletService.delete(id);
    }

}
