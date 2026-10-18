package pe.hogar.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class DatosSinteticosTest {
    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void arrancaElContextoDeSpring() {
        assertNotNull(applicationContext);
    }

    @Test
    void cargaElNucleoSinteticoEnPerfilTest() {
        assertEquals(3, contar("SELECT count(*) FROM residentes.cuenta_acceso"));
        assertEquals(3, contar("SELECT count(*) FROM residentes.expediente"));
        assertEquals(6, contar("SELECT count(*) FROM catalogo.paso p " +
                "JOIN catalogo.plantilla_pasos pp ON pp.id = p.plantilla_id " +
                "JOIN catalogo.actividad a ON a.id = pp.actividad_id WHERE a.codigo = 'A-02'"));
        assertEquals(4, contar("SELECT count(*) FROM catalogo.nivel_apoyo n " +
                "JOIN catalogo.escala_apoyo e ON e.id = n.escala_id WHERE e.version = 1"));
    }

    private int contar(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
