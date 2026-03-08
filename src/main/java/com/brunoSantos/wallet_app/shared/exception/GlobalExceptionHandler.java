package com.brunoSantos.wallet_app.shared.exception;

import com.brunoSantos.wallet_app.shared.dto.ErrorResponse;
import com.brunoSantos.wallet_app.wallet.exception.WalletExistsException;
import com.brunoSantos.wallet_app.wallet.exception.WalletNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WalletExistsException.class)
    public ResponseEntity<ErrorResponse> handleWalletExists() {

        return ResponseEntity.badRequest()
                .body(new ErrorResponse("Wallet already exists"));
    }


    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleWalletNotExists() {

        return ResponseEntity.badRequest()
                .body(new ErrorResponse("Wallet not exists"));
    }

}