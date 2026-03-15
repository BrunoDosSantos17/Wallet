package com.brunoSantos.wallet_app.asset;

import com.brunoSantos.wallet_app.asset.domain.Asset;
import com.brunoSantos.wallet_app.asset.service.AssetPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/teste")
@RequiredArgsConstructor
public class ControllerTest {
    private final AssetPriceService assetPriceService;

    @PostMapping("/assets/{ticker}/update-price")
    public ResponseEntity<Asset> updatePrice(@PathVariable String ticker) {

        var teste = assetPriceService.updatePrice(ticker);

        return ResponseEntity.ok(teste);
    }

}