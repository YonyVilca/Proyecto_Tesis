package pe.hogar.integracion;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integración de la declaración idempotente y descarga de datos del turno (RF-12, RF-90, RF-101, RF-100). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class TurnosTest {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach void limpiarTurnos() {
        jdbc.update("DELETE FROM registro.turno_encadenado");
        jdbc.update("DELETE FROM registro.turno_declarado");
    }

    @Test void declaraTurnoReintentaYEncadenaHastaLasSiete() throws Exception {
        String token = iniciar("cuidador1");
        String codigo = "00000000-0000-0000-0000-000000000101";
        String solicitud = solicitud("M", OffsetDateTime.now(LIMA).withNano(0).withOffsetSameInstant(ZoneOffset.UTC), "");

        declarar(codigo, solicitud, token).andExpect(status().isOk()).andExpect(jsonPath("$.resultado").value("ACEPTADO"));
        declarar(codigo, solicitud, token).andExpect(status().isOk()).andExpect(jsonPath("$.resultado").value("DUPLICADO"));
        declarar(codigo, solicitud("M", instante(solicitud), "\"T\""), token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultado").value("ACEPTADO"));
        var fin = jdbc.queryForObject("SELECT fin_ventana AT TIME ZONE 'America/Lima' FROM registro.turno_declarado WHERE id=?",
                java.time.LocalDateTime.class, java.util.UUID.fromString(codigo));
        var declarado = jdbc.queryForObject("SELECT declarado_en AT TIME ZONE 'America/Lima' FROM registro.turno_declarado WHERE id=?",
                java.time.LocalDateTime.class, java.util.UUID.fromString(codigo));
        assertEquals(instante(solicitud).atZoneSameInstant(LIMA).toLocalDate().atTime(19, 0), fin);
        assertEquals(instante(solicitud).atZoneSameInstant(LIMA).toLocalDateTime(), declarado);
    }

    @Test void rechazaDescansoYEncadenamientoNoContiguo() throws Exception {
        String token = iniciar("cuidador1");
        declarar("00000000-0000-0000-0000-000000000102", solicitud("D", OffsetDateTime.now(LIMA), ""), token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultado").value("RECHAZADO_VALIDACION"));
        declarar("00000000-0000-0000-0000-000000000103",
                solicitud("M", OffsetDateTime.now(LIMA).withNano(0), "\"N\""), token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultado").value("RECHAZADO_VALIDACION"));
    }

    @Test void rechazaSolapamientoDelMismoCuidador() throws Exception {
        String token = iniciar("cuidador1");
        OffsetDateTime ahora = OffsetDateTime.now(LIMA).withNano(0);
        declarar("00000000-0000-0000-0000-000000000104", solicitud("M", ahora, ""), token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultado").value("ACEPTADO"));
        declarar("00000000-0000-0000-0000-000000000105", solicitud("M", ahora, ""), token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultado").value("RECHAZADO_VALIDACION"));
    }

    @Test void descargaDatosVisiblesSinIdentidadYSoloParaElAutor() throws Exception {
        String codigo = "00000000-0000-0000-0000-000000000106";
        String turnoToken = iniciar("cuidador1");
        declarar(codigo, solicitud("M", OffsetDateTime.now(LIMA).withNano(0), ""), turnoToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultado").value("ACEPTADO"));

        MvcResult resultado = mvc.perform(get("/api/turnos/{codigo}/datos", codigo)
                        .header("Authorization", "Bearer " + turnoToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expedientes.length()").value(3))
                .andExpect(jsonPath("$.actividades[?(@.codigo=='A-02')].plantilla.pasos.length()").value(6))
                .andExpect(jsonPath("$.actividades[?(@.codigo=='A-02')].plantilla.pasos[0].orden").value(1))
                .andExpect(jsonPath("$.escala.niveles.length()").value(4))
                .andExpect(jsonPath("$.escala.niveles[0].nombre").value("Independiente")).andReturn();
        JsonNode json = objectMapper.readTree(resultado.getResponse().getContentAsString());
        String cuerpo = json.toString().toLowerCase();
        assertFalse(cuerpo.contains("dni"));
        assertFalse(cuerpo.contains("diagnostico"));

        mvc.perform(get("/api/turnos/{codigo}/datos", codigo).header("Authorization", "Bearer " + iniciar("cuidador2")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/turnos/{codigo}/datos", codigo).header("Authorization", "Bearer " + iniciar("coordinadora")))
                .andExpect(status().isForbidden());
    }

    @Test void coordinadoraNoPuedeDeclararTurno() throws Exception {
        declarar("00000000-0000-0000-0000-000000000107",
                solicitud("M", OffsetDateTime.now(LIMA).withNano(0), ""), iniciar("coordinadora"))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions declarar(String codigo, String solicitud, String token)
            throws Exception {
        return mvc.perform(put("/api/turnos/{codigo}", codigo).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(solicitud));
    }

    private String iniciar(String usuario) throws Exception {
        String cuerpo = mvc.perform(post("/api/sesiones").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + usuario + "\",\"contrasena\":\"desarrollo123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get("token").asText();
    }

    private String solicitud(String tipo, OffsetDateTime declaradoEn, String encadenadoJson) {
        String encadenados = encadenadoJson.isEmpty() ? "" : encadenadoJson;
        return "{\"tipo\":\"" + tipo + "\",\"declaradoEn\":\"" + declaradoEn + "\",\"encadenados\":[" + encadenados + "]}";
    }

    private OffsetDateTime instante(String solicitud) throws Exception {
        return OffsetDateTime.parse(objectMapper.readTree(solicitud).get("declaradoEn").asText());
    }
}
