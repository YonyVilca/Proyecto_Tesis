package pe.hogar.integracion;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class DatosSinteticosTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void cargaElNucleoSinteticoEnPerfilTest() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("filesystem:db/migration", "filesystem:db/desarrollo")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            assertEquals(3, contar(statement, "SELECT count(*) FROM residentes.cuenta_acceso"));
            assertEquals(3, contar(statement, "SELECT count(*) FROM residentes.expediente"));
            assertEquals(6, contar(statement, "SELECT count(*) FROM catalogo.paso p " +
                    "JOIN catalogo.plantilla_pasos pp ON pp.id = p.plantilla_id " +
                    "JOIN catalogo.actividad a ON a.id = pp.actividad_id WHERE a.codigo = 'A-02'"));
            assertEquals(4, contar(statement, "SELECT count(*) FROM catalogo.nivel_apoyo n " +
                    "JOIN catalogo.escala_apoyo e ON e.id = n.escala_id WHERE e.version = 1"));
        }
    }

    private static int contar(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}
