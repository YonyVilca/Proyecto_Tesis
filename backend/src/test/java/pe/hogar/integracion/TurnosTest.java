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
        jdbc.update("DELETE FROM registro.registro_campo_modificado");
        jdbc.update("DELETE FROM registro.registro_paso");
        jdbc.update("DELETE FROM registro.registro");
        jdbc.update("DELETE FROM registro.turno_encadenado");
        jdbc.update("DELETE FROM registro.turno_declarado");
    }

    @Test void recibeRegistroCompletoSinDuplicarlo() throws Exception {
        String token = iniciar("cuidador1");
        OffsetDateTime ahora = OffsetDateTime.now(LIMA).withNano(0);
        String tipo = ahora.getHour() < 7 || ahora.getHour() >= 19 ? "N" : ahora.getHour() < 13 ? "M" : "T";
        String turno = "00000000-0000-0000-0000-000000000121";
        declarar(turno, solicitud(tipo, ahora, ""), token).andExpect(jsonPath("$.resultado").value("ACEPTADO"));
        String codigo = "00000000-0000-0000-0000-000000000122";
        String cuerpo = registroJson(turno, ahora, true, "INDEPENDIENTE", 6);
        enviarRegistro(codigo, cuerpo, token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultado").value("ACEPTADO"));
        enviarRegistro(codigo, cuerpo.replace("INDEPENDIENTE", "VERBAL"), token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultado").value("DUPLICADO"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM registro.registro WHERE id=?",
                Integer.class, java.util.UUID.fromString(codigo)));
        assertEquals(6, jdbc.queryForObject("SELECT count(*) FROM registro.registro_paso WHERE registro_id=?",
                Integer.class, java.util.UUID.fromString(codigo)));

        enviarRegistro("00000000-0000-0000-0000-000000000123",
                registroJson("00000000-0000-0000-0000-000000000199", ahora, true, "INDEPENDIENTE", 6), token)
                .andExpect(jsonPath("$.resultado").value("RECHAZADO_SIN_TURNO"));
        String tokenOtro = iniciar("cuidador2");
        declarar("00000000-0000-0000-0000-000000000124", solicitud(tipo, ahora, ""), tokenOtro)
                .andExpect(jsonPath("$.resultado").value("ACEPTADO"));
        enviarRegistro("00000000-0000-0000-0000-000000000125",
                registroJson("00000000-0000-0000-0000-000000000124", ahora, true, "INDEPENDIENTE", 6), token)
                .andExpect(jsonPath("$.resultado").value("RECHAZADO_SIN_TURNO"));
        enviarRegistro("00000000-0000-0000-0000-000000000126",
                registroJson(turno, ahora.minusDays(2), true, "INDEPENDIENTE", 6), token)
                .andExpect(jsonPath("$.resultado").value("RECHAZADO_FUERA_DE_VENTANA"));
        enviarRegistro("00000000-0000-0000-0000-000000000127",
                registroJson(turno, ahora, true, null, 6), token)
                .andExpect(jsonPath("$.resultado").value("RECHAZADO_VALIDACION"));
        enviarRegistro("00000000-0000-0000-0000-000000000128",
                registroJson(turno, ahora, true, "INDEPENDIENTE", 5), token)
                .andExpect(jsonPath("$.resultado").value("RECHAZADO_VALIDACION"));
        enviarRegistro("00000000-0000-0000-0000-000000000129",
                registroJson(turno, ahora, false, null, 0), token)
                .andExpect(jsonPath("$.resultado").value("ACEPTADO"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM registro.registro_paso WHERE registro_id=?",
                Integer.class, java.util.UUID.fromString("00000000-0000-0000-0000-000000000129")));
    }

    private org.springframework.test.web.servlet.ResultActions enviarRegistro(String codigo, String cuerpo, String token)
            throws Exception {
        return mvc.perform(put("/api/registros/{codigo}", codigo).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private String registroJson(String turno, OffsetDateTime inicio, boolean oportunidad, String nivel, int totalPasos) {
        StringBuilder pasos = new StringBuilder("[");
        for (int i = 1; i <= totalPasos; i++) {
            if (i > 1) pasos.append(',');
            pasos.append("{\"orden\":").append(i).append(",\"cumplido\":true}");
        }
        pasos.append(']');
        String nivelJson = nivel == null ? "" : ",\"nivel\":\"" + nivel + "\"";
        String razon = oportunidad ? "" : ",\"motivoSinOportunidad\":\"FALTAN_RECURSOS\"";
        return "{\"turno\":\"" + turno + "\",\"expediente\":\"00000000-0000-0000-0000-000000000001\","
                + "\"actividad\":\"A-02\",\"versionPlantilla\":1,\"inicioCaptura\":\"" + inicio + "\","
                + "\"finCaptura\":\"" + inicio.plusMinutes(1) + "\",\"origen\":\"TACTIL\",\"oportunidad\":"
                + oportunidad + razon + ",\"noQuiso\":false" + nivelJson + ",\"pasos\":" + pasos
                + ",\"secuenciaCorrecta\":true,\"camposModificados\":[]}";
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
        OffsetDateTime inicio = ahora.toLocalDate().atTime(7, 0).atZone(LIMA).toOffsetDateTime();
        OffsetDateTime fin = ahora.toLocalDate().atTime(13, 0).atZone(LIMA).toOffsetDateTime();
        jdbc.update("INSERT INTO registro.turno_declarado(id, cuenta_id, tipo, declarado_en, inicio_ventana, fin_ventana) " +
                        "SELECT ?, id, 'M'::comun.tipo_turno, ?, ?, ? FROM residentes.cuenta_acceso WHERE usuario=?",
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000104"), ahora, inicio, fin, "cuidador1");
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
