package com.brunoSantos.Wallet.controller;

import com.brunoSantos.Wallet.dto.ActionDto;
import com.brunoSantos.Wallet.service.ServiceWallet;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;


@RestController
@RequestMapping("wallet")
@AllArgsConstructor
public class WalletController {

    private ServiceWallet serviceWallet;

    @GetMapping
    public ResponseEntity<ArrayList<ActionDto>> listAllWallets() {
        return ResponseEntity.ok(serviceWallet.getAllActions());
    }

    @PostMapping
    public ResponseEntity<ActionDto> addActionWallet(@RequestBody ActionDto actionDto) {
        serviceWallet.addAction(actionDto);
        return ResponseEntity.ok(actionDto);
    }

    @DeleteMapping
    public ResponseEntity deleteActionWallet(@RequestAttribute String name) {
        serviceWallet.deleteAction(name);
        return ResponseEntity.ok().build();

    }

    @PutMapping
    public void updateActionWallet(@RequestBody ActionDto actionDto) {
        serviceWallet.updateAction(actionDto);
    }
}
