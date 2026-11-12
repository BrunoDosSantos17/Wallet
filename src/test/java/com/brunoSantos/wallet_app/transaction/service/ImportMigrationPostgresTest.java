package com.brunoSantos.wallet_app.transaction.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariables({
        @EnabledIfEnvironmentVariable(named = "C3_TEST_DB_URL", matches = "jdbc:postgresql:.*"),
        @EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
})
class ImportMigrationPostgresTest {
    @Test
    void migration_blocks_missing_historical_dates_and_enforces_unique_imports() throws Exception {
        String schema = "c4_fixture_" + UUID.randomUUID().toString().replace("-", "");
        String sql = Files.readString(Path.of("src/main/resources/db/legacy-migration/V20261107_01__import_identity_and_history.sql"));
        try (var connection = DriverManager.getConnection(com.brunoSantos.wallet_app.shared.testing.IsolatedPostgres.url(),
                System.getenv().getOrDefault("C3_TEST_DB_USER", "proposaldb"), System.getenv("DB_PASSWORD"));
             var statement = connection.createStatement()) {
            statement.execute("create schema \"" + schema + "\"");
            try {
                statement.execute("set search_path to \"" + schema + "\"");
                statement.execute("create table \"transaction\" (wallet_id bigint, asset_ticker varchar(100), data_transaction date)");
                statement.execute("insert into \"transaction\" values (1, 'PETR4', null)");
                assertThatThrownBy(() -> statement.execute(sql)).isInstanceOf(SQLException.class)
                        .hasMessageContaining("historical transactions have no operation date");
                statement.execute("rollback");
                statement.execute("delete from \"transaction\"");
                statement.execute(sql);
                statement.execute("insert into \"transaction\" values (1, 'PETR4', '2024-01-01', 'abc')");
                assertThatThrownBy(() -> statement.execute("insert into \"transaction\" values (1, 'PETR4', '2024-01-01', 'abc')"))
                        .isInstanceOf(SQLException.class).hasMessageContaining("uk_transaction_wallet_import_identity");
                try (var result = statement.executeQuery("select count(*) from \"transaction\"")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isEqualTo(1);
                }
            } finally {
                statement.execute("drop schema \"" + schema + "\" cascade");
            }
        }
    }
}
