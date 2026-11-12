package com.brunoSantos.wallet_app.transaction.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariables({
        @EnabledIfEnvironmentVariable(named = "C3_TEST_DB_URL", matches = "jdbc:postgresql:.*"),
        @EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
})
class FlywaySchemaPostgresTest {
    @Test
    void creates_schema_once_and_enforces_financial_constraints() throws Exception {
        String url = System.getenv("C3_TEST_DB_URL");
        String user = System.getenv().getOrDefault("C3_TEST_DB_USER", "proposaldb");
        String password = System.getenv("DB_PASSWORD");
        String schema = "flyway_test_" + UUID.randomUUID().toString().replace("-", "");
        Flyway flyway = Flyway.configure().dataSource(url, user, password)
                .schemas(schema).defaultSchema(schema).load();
        try {
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(4);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            flyway.validate();
            try (var connection = DriverManager.getConnection(url, user, password);
                 var sql = connection.createStatement()) {
                sql.execute("SET search_path TO \"" + schema + "\"");
                try (var result = sql.executeQuery("select numeric_precision, numeric_scale from information_schema.columns "
                        + "where table_schema = current_schema() and table_name = 'asset_position' and column_name = 'quantity'")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isEqualTo(38);
                    assertThat(result.getInt(2)).isEqualTo(12);
                }
                sql.execute("insert into wallet(id, name) values (1, 'test')");
                sql.execute("insert into asset(ticker) values ('PETR4')");
                sql.execute("insert into asset_position(wallet_id, asset_ticker, quantity) values (1, 'PETR4', 0.123456789012)");
                assertThatThrownBy(() -> sql.execute("insert into asset_position(wallet_id, asset_ticker) values (1, 'PETR4')"))
                        .hasMessageContaining("uk_asset_position_wallet_asset");
                sql.execute("insert into dividendo_recebido(id_asset_position, valor_dividendo, tipo_provento, data_pagamento, identificador_origem) "
                        + "select id, 0.12345678, 'JCP', '2024-01-01', 'evento-1' from asset_position");
                assertThatThrownBy(() -> sql.execute("insert into dividendo_recebido(id_asset_position, valor_dividendo, tipo_provento, data_pagamento, identificador_origem) "
                        + "select id, 1, 'JCP', '2024-01-01', 'evento-1' from asset_position"))
                        .hasMessageContaining("uk_dividendo_position_origem");
                sql.execute("insert into \"transaction\"(wallet_id, import_identity) values (1, 'abc')");
                assertThatThrownBy(() -> sql.execute("insert into \"transaction\"(wallet_id, import_identity) values (1, 'abc')"))
                        .hasMessageContaining("uk_transaction_wallet_import_identity");
                sql.execute("update flyway_schema_history set checksum = checksum + 1 where version = '1'");
            }
            assertThatThrownBy(flyway::validate).hasMessageContaining("checksum mismatch");
        } finally {
            try (var connection = DriverManager.getConnection(url, user, password);
                 var sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
            }
        }
    }
}
