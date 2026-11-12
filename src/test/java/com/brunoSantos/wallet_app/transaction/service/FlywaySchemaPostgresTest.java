package com.brunoSantos.wallet_app.transaction.service;

import com.brunoSantos.wallet_app.shared.testing.IsolatedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariables({
        @EnabledIfEnvironmentVariable(named = "C3_TEST_DB_URL", matches = "jdbc:postgresql:.*"),
        @EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
})
class FlywaySchemaPostgresTest {
    @Test
    void creates_schema_once_and_validates_checksum() throws Exception {
        String url = IsolatedPostgres.url(), user = System.getenv().getOrDefault("C3_TEST_DB_USER", "proposaldb"), password = System.getenv("DB_PASSWORD");
        String schema = "flyway_test_" + UUID.randomUUID().toString().replace("-", "");
        var flyway = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema).load();
        try {
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(5);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            flyway.validate();
            try (var c = DriverManager.getConnection(url, user, password); var sql = c.createStatement()) {
                sql.execute("SET search_path TO \"" + schema + "\"");
                sql.execute("insert into wallet(id,name) values (1,'test')");
                sql.execute("insert into asset(ticker) values ('T1')");
                sql.execute("insert into asset_position(id,wallet_id,asset_ticker,version) values (1,1,'T1',0)");
                assertThatThrownBy(() -> sql.execute("insert into asset_position(id,wallet_id,asset_ticker,version) values (2,1,'T1',0)"))
                        .hasMessageContaining("uk_asset_position_wallet_asset");
                try (var rows = sql.executeQuery("select numeric_precision,numeric_scale from information_schema.columns where table_schema=current_schema() and table_name='asset_position' and column_name='quantity'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(38);
                    assertThat(rows.getInt(2)).isEqualTo(12);
                }
                sql.execute("insert into dividendo_recebido(id_asset_position,valor_dividendo,tipo_provento,data_pagamento,identificador_origem) values (1,0.12345678,'JCP','2024-01-01','evento-1')");
                assertThatThrownBy(() -> sql.execute("insert into dividendo_recebido(id_asset_position,valor_dividendo,tipo_provento,data_pagamento,identificador_origem) values (1,1,'JCP','2024-01-01','evento-1')"))
                        .hasMessageContaining("uk_dividendo_position_origem");
                sql.execute("insert into \"transaction\"(wallet_id,import_identity) values (1,'abc')");
                assertThatThrownBy(() -> sql.execute("insert into \"transaction\"(wallet_id,import_identity) values (1,'abc')"))
                        .hasMessageContaining("uk_transaction_wallet_import_identity");
                sql.execute("update flyway_schema_history set checksum = checksum + 1 where version = '1'");
            }
            assertThatThrownBy(flyway::validate).hasMessageContaining("checksum mismatch");
        } finally { drop(url, user, password, schema); }
    }
    @Test
    void upgrades_v4_without_losing_existing_data() throws Exception {
        String url = IsolatedPostgres.url(), user = System.getenv().getOrDefault("C3_TEST_DB_USER", "proposaldb"), password = System.getenv("DB_PASSWORD");
        String schema = "flyway_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).target("4").load().migrate();
            try (var c = DriverManager.getConnection(url,user,password); var sql=c.createStatement()) {
                sql.execute("set search_path to \"" + schema + "\"");
                sql.execute("insert into wallet(id,name) values (1,'test')");
                sql.execute("insert into asset(ticker) values ('T1')");
                sql.execute("insert into asset_position(id,wallet_id,asset_ticker,version) values (1,1,'T1',0)");
                sql.execute("insert into dividendo_recebido(id_asset_position,valor_dividendo,tipo_provento,data_pagamento) values (1,1,'JCP',current_date)");
            }
            assertThat(Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).load().migrate().migrationsExecuted).isEqualTo(1);
            try (var c = DriverManager.getConnection(url,user,password); var sql=c.createStatement()) {
                sql.execute("set search_path to \"" + schema + "\"");
                try (var rows=sql.executeQuery("select count(*) from dividendo_recebido")) { assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isEqualTo(1); }
                assertThatThrownBy(() -> sql.execute("delete from asset_position where id=1")).isInstanceOf(java.sql.SQLException.class);
            }
        } finally { drop(url, user, password, schema); }
    }
    private void drop(String url, String user, String password, String schema) throws Exception {
        try (var c=DriverManager.getConnection(url,user,password); var sql=c.createStatement()) { sql.execute("drop schema if exists \"" + schema + "\" cascade"); }
    }
}
