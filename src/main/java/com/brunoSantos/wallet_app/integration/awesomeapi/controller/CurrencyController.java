package com.brunoSantos.wallet_app.integration.awesomeapi.controller;

import com.brunoSantos.wallet_app.integration.awesomeapi.dto.AwesomeApiQuote;
import com.brunoSantos.wallet_app.integration.awesomeapi.service.AwesomeApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/currencies")
@RequiredArgsConstructor
@Tag(name = "Moedas", description = "Consulta de cotações de moedas pela AwesomeAPI")
public class CurrencyController {

    private final AwesomeApiService awesomeApiService;

    @GetMapping("/{pair}")
    @Operation(summary = "Consultar cotação de um par de moedas", description = "Retorna a cotação mais recente de um único par, por exemplo USD-BRL.")
    public AwesomeApiQuote getLatestQuote(
            @Parameter(description = "Par de moedas no formato XXX-YYY", example = "USD-BRL")
            @PathVariable("pair") String pair) {
        return awesomeApiService.getLatestQuote(pair);
    }
}
