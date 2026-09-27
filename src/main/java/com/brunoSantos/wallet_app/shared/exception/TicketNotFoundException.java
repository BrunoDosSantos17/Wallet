package com.brunoSantos.wallet_app.shared.exception;

import lombok.Getter;

@Getter
public class TicketNotFoundException extends RuntimeException {

    private final int code;

    public TicketNotFoundException(int code, String message) {
        super(message);
        this.code = code;
    }
}
