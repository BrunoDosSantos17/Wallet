package com.brunoSantos.Wallet.service;

import com.brunoSantos.Wallet.dto.ActionDto;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@AllArgsConstructor
@Service
public class ServiceWallet {

    ArrayList<ActionDto> actions;

    public void addAction(ActionDto actionDto) {
        actions.add(actionDto);
    }

    public ArrayList<ActionDto> getAllActions() {
        return actions;
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
