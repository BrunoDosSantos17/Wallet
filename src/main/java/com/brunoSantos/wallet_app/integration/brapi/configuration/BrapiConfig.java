package com.brunoSantos.wallet_app.integration.brapi.configuration;

import com.brunoSantos.wallet_app.integration.brapi.client.BrapiClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class BrapiConfig {

    @Bean
    public BrapiClient brapiClient() {

        var restClient = RestClient.builder()
                .baseUrl("https://brapi.dev/api")
                .build();

        var factory =
                HttpServiceProxyFactory.builderFor(
                        RestClientAdapter.create(restClient)
                ).build();

        return factory.createClient(BrapiClient.class);
    }
}
