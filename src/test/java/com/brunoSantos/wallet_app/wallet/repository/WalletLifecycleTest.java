package com.brunoSantos.wallet_app.wallet.repository;

import com.brunoSantos.wallet_app.shared.testing.IsolatedPostgres;
import com.brunoSantos.wallet_app.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariables({
        @EnabledIfEnvironmentVariable(named = "C3_TEST_DB_URL", matches = "jdbc:postgresql:.*"),
        @EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
})
class WalletLifecycleTest {
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", IsolatedPostgres::url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("C3_TEST_DB_USER", "proposaldb"));
        registry.add("spring.datasource.password", () -> System.getenv("DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("brapi.token", () -> "test-token-not-used");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired WalletService service;

    @Test
    void deleting_wallet_cascades_history_but_preserves_other_wallet_and_shared_asset() {
        String ticker = "T1" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Long first = null, second = null;
        try {
            jdbc.update("insert into asset(ticker) values (?)", ticker);
            first = jdbc.queryForObject("insert into wallet(name) values (?) returning id", Long.class, "T1 delete first");
            second = jdbc.queryForObject("insert into wallet(name) values (?) returning id", Long.class, "T1 delete second");
            long p1 = position(first, ticker);
            long p2 = position(second, ticker);
            for (long wallet : new long[]{first, second})
                jdbc.update("insert into \"transaction\"(wallet_id, asset_ticker, type, quantity, price) values (?, ?, 'BUY', 1, 1)", wallet, ticker);
            for (long position : new long[]{p1, p2})
                jdbc.update("insert into dividendo_recebido(id_asset_position, valor_dividendo, tipo_provento, data_pagamento) values (?, 1, 'DIVIDENDO', current_date)", position);
            assertThatThrownBy(() -> jdbc.update("delete from asset_position where id = ?", p1))
                    .isInstanceOf(DataIntegrityViolationException.class);
            service.delete(first);
            assertThat(count("select count(*) from wallet where id = ?", first)).isZero();
            assertThat(count("select count(*) from asset_position where wallet_id = ?", first)).isZero();
            assertThat(count("select count(*) from \"transaction\" where wallet_id = ?", first)).isZero();
            assertThat(count("select count(*) from dividendo_recebido where id_asset_position = ?", p1)).isZero();
            assertThat(count("select count(*) from wallet where id = ?", second)).isEqualTo(1);
            assertThat(count("select count(*) from asset_position where id = ?", p2)).isEqualTo(1);
            assertThat(count("select count(*) from \"transaction\" where wallet_id = ?", second)).isEqualTo(1);
            assertThat(count("select count(*) from dividendo_recebido where id_asset_position = ?", p2)).isEqualTo(1);
            assertThat(count("select count(*) from asset where ticker = ?", ticker)).isEqualTo(1);
            assertThatThrownBy(() -> jdbc.update("delete from asset where ticker = ?", ticker))
                    .isInstanceOf(DataIntegrityViolationException.class);
            long deletedWalletId = first;
            assertThatThrownBy(() -> jdbc.update("insert into asset_position(wallet_id, asset_ticker, version) values (?, ?, 0)", deletedWalletId, ticker))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            if (first != null) cleanup(first);
            if (second != null) cleanup(second);
            jdbc.update("delete from asset where ticker = ?", ticker);
        }
    }

    @Test
    void failed_wallet_deletion_rolls_back_prior_dividend_deletion() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String table = "t1_fk_" + suffix;
        String ticker = "T1" + suffix;
        Long wallet = null;
        try {
            jdbc.update("insert into asset(ticker) values (?)", ticker);
            wallet = jdbc.queryForObject("insert into wallet(name) values ('T1 rollback') returning id", Long.class);
            long position = position(wallet, ticker);
            jdbc.update("insert into dividendo_recebido(id_asset_position, valor_dividendo, tipo_provento, data_pagamento) values (?, 1, 'JCP', current_date)", position);
            jdbc.execute("create table " + table + " (wallet_id bigint references wallet(id))");
            jdbc.update("insert into " + table + "(wallet_id) values (?)", wallet);
            long walletId = wallet;
            assertThatThrownBy(() -> service.delete(walletId)).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(count("select count(*) from wallet where id = ?", walletId)).isEqualTo(1);
            assertThat(count("select count(*) from asset_position where id = ?", position)).isEqualTo(1);
            assertThat(count("select count(*) from dividendo_recebido where id_asset_position = ?", position)).isEqualTo(1);
        } finally {
            jdbc.execute("drop table if exists " + table);
            if (wallet != null) cleanup(wallet);
            jdbc.update("delete from asset where ticker = ?", ticker);
        }
    }

    private void cleanup(long wallet) {
        jdbc.update("delete from dividendo_recebido where id_asset_position in (select id from asset_position where wallet_id = ?)", wallet);
        jdbc.update("delete from wallet where id = ?", wallet);
    }

    private long position(long wallet, String ticker) {
        return jdbc.queryForObject("insert into asset_position(wallet_id, asset_ticker, version, quantity, average_price) values (?, ?, 0, 1, 1) returning id", Long.class, wallet, ticker);
    }
    private int count(String sql, Object arg) { return jdbc.queryForObject(sql, Integer.class, arg); }
}
