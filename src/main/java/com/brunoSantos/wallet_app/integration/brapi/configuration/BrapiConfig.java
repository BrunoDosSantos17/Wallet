package com.brunoSantos.wallet_app.integration.brapi.configuration;

import com.brunoSantos.wallet_app.integration.brapi.client.BrapiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.time.Duration;

@Configuration
public class BrapiConfig {

    @Value("${brapi.token}")
    private String token;

    @Bean
    public BrapiClient brapiClient() {
        var restClient = RestClient.builder()
                .baseUrl("https://brapi.dev/api")
                .requestFactory(requestFactory())
                .build();

        var factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();

        return factory.createClient(BrapiClient.class);
    }

    SimpleClientHttpRequestFactory requestFactory() {
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(3));
        requests.setReadTimeout(Duration.ofSeconds(10));
        return requests;
    }

    @Bean
    public String brapiToken() {
        return token;
    }
}
