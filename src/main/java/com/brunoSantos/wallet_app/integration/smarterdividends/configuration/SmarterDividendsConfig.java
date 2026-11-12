package com.brunoSantos.wallet_app.integration.smarterdividends.configuration;

import com.brunoSantos.wallet_app.integration.smarterdividends.client.SmarterDividendsClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.time.Duration;

@Configuration
public class SmarterDividendsConfig {
    @Bean
    SmarterDividendsClient smarterDividendsClient() {
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(3));
        requests.setReadTimeout(Duration.ofSeconds(10));
        var client = RestClient.builder().baseUrl("https://smarterdividends.com").requestFactory(requests).build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client)).build().createClient(SmarterDividendsClient.class);
    }
}
