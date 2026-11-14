package com.brunoSantos.wallet_app.integration.awesomeapi.configuration;

import com.brunoSantos.wallet_app.integration.awesomeapi.client.AwesomeApiClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.time.Duration;

@Configuration
public class AwesomeApiConfig {

    @Bean
    public AwesomeApiClient awesomeApiClient() {
        var restClient = RestClient.builder()
                .baseUrl("https://economia.awesomeapi.com.br")
                .requestFactory(requestFactory())
                .build();

        var factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();

        return factory.createClient(AwesomeApiClient.class);
    }

    SimpleClientHttpRequestFactory requestFactory() {
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(3));
        requests.setReadTimeout(Duration.ofSeconds(10));
        return requests;
    }
}
