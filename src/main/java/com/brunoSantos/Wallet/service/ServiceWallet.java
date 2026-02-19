package com.brunoSantos.Wallet.service;

import com.brunoSantos.Wallet.client.BrapiClient;
import com.brunoSantos.Wallet.dto.ActionDto;
import com.brunoSantos.Wallet.entity.Stock;
import com.brunoSantos.Wallet.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
public class ServiceWallet {

    @Value("${environment.TOKEN:}")
    private String token;

    private final StockRepository stockRepository;

    private final BrapiClient  brapiClient;

    private final ArrayList<ActionDto> actions;

    public void addAction(ActionDto actionDto) {

        var stock = brapiClient.getStock(token, actionDto.name());

        stockRepository.save(Stock.builder()
                        .name(actionDto.name())
                        .qtd(actionDto.qtd())
                        .price(BigDecimal.valueOf(actionDto.qtd() * stock.results().getFirst().regularMarketPrice()))
                .build());
    }

    public List<Stock> getAllActions() {
        return stockRepository.findAll();
    }

    public ActionDto getActionByName(String actionName) {
        return actions.stream().filter(actionDto -> actionDto.name().equals(actionName))
                .findFirst()
                .orElse(null);
    }

    public void deleteAction(String actionName) {
        actions.remove(getActionByName(actionName));
    }

    public void updateAction(ActionDto actionDto) {
        actions.remove(getActionByName(actionDto.name()));
        actions.add(actionDto);
    }
}
