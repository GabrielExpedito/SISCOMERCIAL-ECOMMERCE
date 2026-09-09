package com.siscomercial.ecommerce.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PedidoMigrationsTest {

    @Test
    void deveAplicarMigrationsDePedidoDepoisDoBaseline() throws Exception {
        String url = "jdbc:h2:mem:flyway_pedido;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE cliente (id BIGINT PRIMARY KEY, cpf_cnpj VARCHAR(30) NOT NULL, senha_hash VARCHAR(255) NOT NULL)");
            statement.execute("CREATE TABLE pedido (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE produto (id BIGINT PRIMARY KEY)");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(url, "sa", "")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .locations("classpath:db/migration")
                .load();

        assertEquals(6, flyway.migrate().migrationsExecuted);

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            var historico = statement.executeQuery("SELECT COUNT(*) FROM historico_status_pedido");
            historico.next();
            assertEquals(0, historico.getInt(1));

            var googleId = statement.executeQuery(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'CLIENTE' AND COLUMN_NAME = 'GOOGLE_ID'");
            googleId.next();
            assertEquals(1, googleId.getInt(1));

            var integracao = statement.executeQuery("SELECT COUNT(*) FROM integracao_marketplace");
            integracao.next();
            assertEquals(0, integracao.getInt(1));

            var oauthState = statement.executeQuery(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'INTEGRACAO_MARKETPLACE' AND COLUMN_NAME = 'OAUTH_STATE'");
            oauthState.next();
            assertEquals(1, oauthState.getInt(1));
        }
    }
}
