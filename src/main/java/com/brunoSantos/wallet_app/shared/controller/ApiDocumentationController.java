package com.brunoSantos.wallet_app.shared.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiDocumentationController {

    private static final String SCALAR_PAGE = """
            <!doctype html>
            <html lang="pt-BR">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Wallet API — Documentação</title>
            </head>
            <body>
              <script id="api-reference" data-url="/v3/api-docs"></script>
              <script src="https://cdn.jsdelivr.net/npm/@scalar/api-reference"></script>
            </body>
            </html>
            """;

    @GetMapping(value = "/scalar", produces = MediaType.TEXT_HTML_VALUE)
    public String scalar() {
        return SCALAR_PAGE;
    }
}
