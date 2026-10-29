package com.brunoSantos.wallet_app.wallet.controller;

import com.brunoSantos.wallet_app.wallet.dto.CreateWalletRequest;
import com.brunoSantos.wallet_app.wallet.dto.UpdateWalletRequest;
import com.brunoSantos.wallet_app.wallet.dto.WalletBasicInfoResponse;
import com.brunoSantos.wallet_app.wallet.dto.WalletResponse;
import com.brunoSantos.wallet_app.wallet.service.WalletService;
import com.brunoSantos.wallet_app.asset.service.AssetPriceScheduler;
import com.brunoSantos.wallet_app.asset.service.AssetPriceSchedulerAction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
@Tag(name = "Carteiras", description = "Gerenciamento e consulta de carteiras")
public class WalletController {

    private final WalletService walletService;
    private final AssetPriceScheduler assetPriceScheduler;

    @PostMapping("/scheduler/asset-price/{acao}")
    @Operation(summary = "Controlar atualização automática", description = "Ativa ou desativa a atualização periódica de preços. A sincronização manual continua disponível independentemente desta configuração.")
    public ResponseEntity<String> setAssetPriceScheduler(@PathVariable("acao") AssetPriceSchedulerAction action) {
        return switch (action) {
            case ATIVAR -> {
                assetPriceScheduler.start();
                yield ResponseEntity.ok("Atualização automática de preços ativada");
            }
            case DESATIVAR -> {
                boolean disabled = assetPriceScheduler.disable();
                String message = disabled
                        ? "Atualização automática de preços desativada"
                        : "A atualização automática de preços já estava desativada";
                yield ResponseEntity.ok(message);
            }
        };
    }

    @PostMapping
    @Operation(summary = "Criar carteira")
    public ResponseEntity<WalletResponse> create(@RequestBody @Valid CreateWalletRequest request) {

        var wallet = walletService.createWallet(request.name());

        return ResponseEntity.ok(
                WalletResponse.fromEntity(wallet)
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar carteira por identificador")
    public WalletResponse findById(@PathVariable Long id) {
        var wallet = walletService.findById(id);
        return WalletResponse.fromEntity(wallet);
    }

    @GetMapping
    @Operation(summary = "Listar carteiras")
    public List<WalletResponse> findAll() {
        return walletService.findAll()
                .stream()
                .map(WalletResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @GetMapping("/all")
    @Operation(summary = "Listar todas as carteiras")
    public List<WalletResponse> findAllWallets() {
        return walletService.findAll()
                .stream()
                .map(WalletResponse::fromEntity)
                .toList();
    }

    @GetMapping("/infoBasica/{walletId}")
    @Operation(summary = "Consultar resumo da carteira")
    public WalletBasicInfoResponse infoBasica(@PathVariable Long walletId) {
        return walletService.infoBasica(walletId);
    }

    @GetMapping("/infoBasica/{walletId}/atualizar")
    @Operation(summary = "Sincronizar ativos da carteira e consultar resumo", description = "Consulta os preços atuais dos ativos da carteira no provedor Brapi e retorna o resumo atualizado. Não depende do estado do agendador automático.")
    public WalletBasicInfoResponse atualizarInfoBasica(@PathVariable Long walletId) {
        return walletService.atualizarInfoBasica(walletId);
    }

    @PutMapping
    @Operation(summary = "Renomear carteira")
    public WalletResponse renameWallet(@RequestBody UpdateWalletRequest updateWalletRequest) {
        return WalletResponse.fromEntity(walletService.update(updateWalletRequest));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir carteira")
    public void delete(@PathVariable Long id) {
        walletService.delete(id);
    }

}
