package com.brunoSantos.wallet_app.position.repository;

import com.brunoSantos.wallet_app.shared.testing.IsolatedPostgres;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariables({
        @EnabledIfEnvironmentVariable(named = "C3_TEST_DB_URL", matches = "jdbc:postgresql:.*"),
        @EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
})
class PostgresPrecisionLimitsTest {
    @Test
    void database_round_trips_maximum_values_and_rejects_overflow() throws Exception {
        String url = IsolatedPostgres.url();
        String user = System.getenv().getOrDefault("C3_TEST_DB_USER", "proposaldb");
        String password = System.getenv("DB_PASSWORD");
        Flyway.configure().dataSource(url, user, password).load().migrate();
        try (var connection = DriverManager.getConnection(url, user, password)) {
            connection.setAutoCommit(false);
            try (var sql = connection.createStatement()) {
                String ticker = "LIMIT" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                var price = new BigDecimal("999999999999999999999999999999.99999999");
                var quantity = new BigDecimal("99999999999999999999999999.999999999999");
                sql.execute("insert into wallet(name) values ('precision limit')");
                long wallet;
                try (var rows = sql.executeQuery("select currval(pg_get_serial_sequence('wallet','id'))")) { rows.next(); wallet = rows.getLong(1); }
                try (var insert = connection.prepareStatement("insert into asset(ticker, current_price) values (?, ?)") ) {
                    insert.setString(1, ticker); insert.setBigDecimal(2, price); insert.executeUpdate();
                }
                try (var insert = connection.prepareStatement("insert into asset_position(wallet_id, asset_ticker, version, quantity, average_price) values (?, ?, 0, ?, ?)") ) {
                    insert.setLong(1, wallet); insert.setString(2, ticker); insert.setBigDecimal(3, quantity); insert.setBigDecimal(4, price); insert.executeUpdate();
                }
                try (var rows = sql.executeQuery("select quantity, average_price from asset_position where wallet_id = " + wallet)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getBigDecimal(1)).isEqualByComparingTo(quantity);
                    assertThat(rows.getBigDecimal(2)).isEqualByComparingTo(price);
                }
                var savepoint = connection.setSavepoint();
                assertThatThrownBy(() -> sql.execute("update asset set current_price = 1e30 where ticker = '" + ticker + "'"))
                        .isInstanceOf(SQLException.class).satisfies(ex -> assertThat(((SQLException) ex).getSQLState()).isEqualTo("22003"));
                connection.rollback(savepoint);
                savepoint = connection.setSavepoint();
                assertThatThrownBy(() -> sql.execute("update asset_position set quantity = 1e26 where wallet_id = " + wallet))
                        .isInstanceOf(SQLException.class).satisfies(ex -> assertThat(((SQLException) ex).getSQLState()).isEqualTo("22003"));
                connection.rollback(savepoint);
            } finally {
                connection.rollback();
            }
        }
    }
}
