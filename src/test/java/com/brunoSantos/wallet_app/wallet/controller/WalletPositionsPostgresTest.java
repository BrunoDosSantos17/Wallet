package com.brunoSantos.wallet_app.wallet.controller;

import com.brunoSantos.wallet_app.shared.testing.IsolatedPostgres;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariables({
        @EnabledIfEnvironmentVariable(named = "C3_TEST_DB_URL", matches = "jdbc:postgresql:.*"),
        @EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
})
class WalletPositionsPostgresTest {
    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", IsolatedPostgres::url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("C3_TEST_DB_USER", "proposaldb"));
        registry.add("spring.datasource.password", () -> System.getenv("DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("brapi.token", () -> "test-token-not-used");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    @Transactional
    void get_wallet_reads_postgres_positions_and_serializes_exact_decimal_market_and_cost() throws Exception {
        long wallet = wallet();
        position(wallet, "STOCK", "STOCK", "1.500000000000", "30.00000000", "40.00000000", "2026-11-14 10:15:30");
        position(wallet, "FII", "FII", "0.000000000000", "0.00000000", "10.00000000", null);
        position(wallet, "ETF", "ETF", "2.000000000000", "5.00000000", null, "2026-01-01 00:00:00");
        position(wallet, "BDR", "BDR", "1.000000000000", "4.00000000", "0.00000000", null);
        position(wallet, "FIXED", "FIXED_INCOME", "1.000000000000", "3.00000000", "4.00000000", null);
        position(wallet, "ABROAD", "STOCK_ABROAD", "1.000000000000", "3.00000000", "4.00000000", null);

        String body = mvc.perform(get("/wallet/{id}", wallet)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // Assert the raw response, rather than JsonPath's double coercion, to check decimal scale and JSON types.
        assertThat(body).contains("\"id\":" + wallet, "\"assetType\":\"STOCK\"",
                "\"quantity\":1.500000000000", "\"averagePrice\":30.00000000",
                "\"totalCost\":45.00", "\"currentPrice\":40.00000000",
                "\"marketValue\":60.00", "\"lastPriceUpdate\":\"2026-11-14T10:15:30\"");
        assertThat(body).contains("\"assetType\":\"FII\"", "\"assetType\":\"ETF\"",
                "\"assetType\":\"BDR\"", "\"assetType\":\"STOCK_ABROAD\"",
                "\"assetType\":\"FIXED_INCOME\"");
        assertThat(body).containsPattern("\"ticker\":\"I1FII[^\"]*\"[^}]*\"marketValue\":0\\.00");
        assertThat(body).containsPattern("\"ticker\":\"I1ETF[^\"]*\"[^}]*\"totalCost\":10\\.00,\"currentPrice\":null,\"marketValue\":null,\"lastPriceUpdate\":null");
        // PostgreSQL stores NUMERIC(38,8) zero as 0E-8, a valid JSON decimal number.
        assertThat(body).containsPattern("\"ticker\":\"I1BDR[^\"]*\"[^}]*\"currentPrice\":0E-8,\"marketValue\":0\\.00");
        // GET must not refresh prices or change persisted historical costs (C5/C8).
        assertThat(jdbc.queryForObject("select current_price from asset where ticker like 'I1STOCK%'", BigDecimal.class))
                .isEqualByComparingTo("40.00000000");
    }

    @Test
    @Transactional
    void missing_wallet_returns_not_found_and_empty_wallet_has_empty_positions() throws Exception {
        long wallet = wallet();
        String body = mvc.perform(get("/wallet/{id}", wallet)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("\"assetPositions\":[]");
        mvc.perform(get("/wallet/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    private long wallet() {
        return jdbc.queryForObject("insert into wallet(name) values ('I1 QA') returning id", Long.class);
    }

    private void position(long wallet, String suffix, String type, String quantity, String cost, String price, String updated) {
        String ticker = "I1" + suffix + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("insert into asset(ticker, name, type, current_price, last_update) values (?, ?, ?, ?, ?::timestamp)",
                ticker, suffix, type, price == null ? null : new BigDecimal(price), updated);
        jdbc.update("insert into asset_position(wallet_id, asset_ticker, version, quantity, average_price) values (?, ?, 0, ?, ?)",
                wallet, ticker, new BigDecimal(quantity), new BigDecimal(cost));
    }
}
