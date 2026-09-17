package com.siscomercial.ecommerce.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PedidoMigrationsTest {

    @Test
    void deveAplicarMigrationsDePedidoDepoisDoBaseline() throws Exception {
        String url = "jdbc:h2:mem:flyway_pedido;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {

            statement.execute(
                    "CREATE TABLE cliente (" +
                            "id BIGINT PRIMARY KEY, " +
                            "cpf_cnpj VARCHAR(30) NOT NULL, " +
                            "senha_hash VARCHAR(255) NOT NULL" +
                            ")"
            );

            statement.execute(
                    "CREATE TABLE pedido (" +
                            "id BIGINT PRIMARY KEY" +
                            ")"
            );

            statement.execute(
                    "CREATE TABLE produto (" +
                            "id BIGINT PRIMARY KEY" +
                            ")"
            );
        }

        Path migrationDirectory = materializarMigrationsCompativeisComH2();

        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(url, "sa", "")
                    .baselineOnMigrate(true)
                    .baselineVersion("1")
                    .locations("filesystem:" + migrationDirectory.toAbsolutePath())
                    .load();

            assertEquals(10, flyway.migrate().migrationsExecuted);

            try (Connection connection = DriverManager.getConnection(url, "sa", "");
                 Statement statement = connection.createStatement()) {

                var historico = statement.executeQuery(
                        "SELECT COUNT(*) FROM historico_status_pedido"
                );
                historico.next();
                assertEquals(0, historico.getInt(1));

                var googleId = statement.executeQuery(
                        "SELECT COUNT(*) " +
                                "FROM INFORMATION_SCHEMA.COLUMNS " +
                                "WHERE TABLE_NAME = 'CLIENTE' " +
                                "AND COLUMN_NAME = 'GOOGLE_ID'"
                );
                googleId.next();
                assertEquals(1, googleId.getInt(1));

                var integracao = statement.executeQuery(
                        "SELECT COUNT(*) FROM integracao_marketplace"
                );
                integracao.next();
                assertEquals(0, integracao.getInt(1));

                var oauthState = statement.executeQuery(
                        "SELECT COUNT(*) " +
                                "FROM INFORMATION_SCHEMA.COLUMNS " +
                                "WHERE TABLE_NAME = 'INTEGRACAO_MARKETPLACE' " +
                                "AND COLUMN_NAME = 'OAUTH_STATE'"
                );
                oauthState.next();
                assertEquals(1, oauthState.getInt(1));

                var pedidosMarketplace = statement.executeQuery(
                        "SELECT COUNT(*) FROM importacao_pedido_marketplace"
                );
                pedidosMarketplace.next();
                assertEquals(0, pedidosMarketplace.getInt(1));

                var ultimaSyncPedidos = statement.executeQuery(
                        "SELECT COUNT(*) " +
                                "FROM INFORMATION_SCHEMA.COLUMNS " +
                                "WHERE TABLE_NAME = 'INTEGRACAO_MARKETPLACE' " +
                                "AND COLUMN_NAME = 'ULTIMA_SINCRONIZACAO_PEDIDOS'"
                );
                ultimaSyncPedidos.next();
                assertEquals(1, ultimaSyncPedidos.getInt(1));
            }

        } finally {
            apagarDiretorioTemporario(migrationDirectory);
        }
    }

    /**
     * O PostgreSQL aceita múltiplos ADD COLUMN no mesmo ALTER TABLE.
     * O H2 utilizado no teste não aceita essa forma, mesmo em
     * MODE=PostgreSQL.
     *
     * Por isso somente a cópia temporária da V9 é adaptada para
     * o teste. As migrations reais do projeto permanecem intactas.
     */
    private Path materializarMigrationsCompativeisComH2() throws Exception {
        Path directory = Files.createTempDirectory(
                "siscomercial-migrations-"
        );

        List<String> migrationNames = List.of(
                "V2__adiciona_google_id_cliente.sql",
                "V3__ajusta_campos_cadastro_google.sql",
                "V4__criar_sequence_pedido.sql",
                "V5__cria_historico_status_pedido.sql",
                "V6__cria_modelo_integracao_marketplace.sql",
                "V7__adiciona_estado_oauth_integracao_marketplace.sql",
                "V8__garante_imagens_produto.sql",
                "V9__adiciona_categoria_mercado_livre_produto.sql",
                "V10__adiciona_categoria_e_atributos_mercado_livre_produto.sql",
                "V11__cria_importacao_pedidos_marketplace.sql"
        );

        try {
            for (String migrationName : migrationNames) {

                String resource = "/db/migration/" + migrationName;

                String sql;

                try (var inputStream = getClass().getResourceAsStream(resource)) {

                    if (inputStream == null) {
                        throw new IllegalStateException(
                                "Migration não encontrada: " + resource
                        );
                    }

                    sql = new String(
                            inputStream.readAllBytes(),
                            StandardCharsets.UTF_8
                    );
                }

                /*
                 * A V9 original possui dois ADD COLUMN no mesmo ALTER TABLE.
                 * O PostgreSQL aceita essa sintaxe, mas o H2 utilizado no
                 * teste não aceita.
                 *
                 * Adaptamos somente a cópia temporária utilizada pelo teste.
                 */
                if ("V9__adiciona_categoria_mercado_livre_produto.sql"
                        .equals(migrationName)) {

                    sql = sql.replace(
                            "ALTER TABLE produto\n" +
                                    "    ADD COLUMN categoria_mercado_livre_id VARCHAR(40),\n" +
                                    "    ADD COLUMN categoria_mercado_livre_nome VARCHAR(255);",

                            "ALTER TABLE produto\n" +
                                    "    ADD COLUMN categoria_mercado_livre_id VARCHAR(40);\n\n" +
                                    "ALTER TABLE produto\n" +
                                    "    ADD COLUMN categoria_mercado_livre_nome VARCHAR(255);"
                    );
                }

                Files.writeString(
                        directory.resolve(migrationName),
                        sql,
                        StandardCharsets.UTF_8
                );
            }

            return directory;

        } catch (Exception exception) {
            apagarDiretorioTemporario(directory);
            throw exception;
        }
    }

    private void apagarDiretorioTemporario(Path directory) {

        if (directory == null) {
            return;
        }

        try (Stream<Path> paths = Files.walk(directory)) {

            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {

                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                            // Melhor esforço de limpeza.
                        }
                    });

        } catch (Exception ignored) {
            // Melhor esforço de limpeza.
        }
    }
}