package com.brunoSantos.wallet_app.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI walletOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Wallet API")
                .description("API para gerenciamento de carteiras, operações e atualização de ativos.")
                .version("1.0.0"));
    }
}
