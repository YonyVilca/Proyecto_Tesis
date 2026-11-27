package pe.hogar.integracion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.hogar.dominio.registro.TipoTurno;
import pe.hogar.dominio.registro.TurnoDeclarado;
import pe.hogar.dominio.residentes.CuentaAcceso;
import pe.hogar.dominio.residentes.Rol;

/** Verifica tarjetas privadas, roles autorizados y consulta del fundamento (RF-53, RF-99, RF-100). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
class ResidentesTest {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final UUID TURNO_SINTETICO = UUID.fromString("00000000-0000-0000-0000-000000000401");
    private static final UUID REGISTRO_SINTETICO = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final CuentaAcceso CUENTA_SINTETICA = new CuentaAcceso("CUI-01", "Cuidador Uno", "cuidador1",
            "hash-sintetico", Rol.CUIDADOR, true);
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void prepararParametrosSinteticos() {
        jdbc.update("DELETE FROM registro.registro WHERE id=?", REGISTRO_SINTETICO);
        jdbc.update("DELETE FROM registro.turno_declarado WHERE id=?", TURNO_SINTETICO);
        insertarParametro("analisis.ventana_n", 10);
        insertarParametro("analisis.minimo_registros", 5);
        insertarParametro("analisis.criterio_dominio", 3);
        insertarParametro("analisis.umbral_cobertura", 0.6);
        insertarParametro("analisis.sin_oportunidad_minimo", 3);
        insertarParametro("analisis.sin_oportunidad_proporcion", 0.3);
        insertarParametro("analisis.ventana_negativas_dias", 7);
    }

    @Test
    void requiereSesionYPermiteConsultarConAmbosRolesSinAtributosRestringidos() throws Exception {
        mvc.perform(get("/api/residentes")).andExpect(status().isUnauthorized());

        String cuidador = iniciar("cuidador1");
        String respuesta = mvc.perform(get("/api/residentes").header("Authorization", "Bearer " + cuidador))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].nombreVisible").value("Ernesto Quispe"))
                .andExpect(jsonPath("$[0].iniciales").exists()).andExpect(jsonPath("$[0].color").exists())
                .andReturn().getResponse().getContentAsString();
        assertFalse(respuesta.contains("diagnostico"));
        assertFalse(respuesta.contains("dni"));
        assertFalse(respuesta.contains("telefono"));

        String coordinadora = iniciar("coordinadora");
        mvc.perform(get("/api/residentes").header("Authorization", "Bearer " + coordinadora))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].nombreVisible").value("Ernesto Quispe"));
    }

    @Test
    void devuelveFundamentoYRegistrosOrigenParaLaActividadSeleccionada() throws Exception {
        String token = iniciar("cuidador1");
        JsonNode tarjetas = json.readTree(mvc.perform(get("/api/residentes")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode residente = tarjetas.get(0);
        JsonNode actividad = residente.get("actividades").get(0);

        mvc.perform(get("/api/residentes/{codigo}/actividades/{actividad}/fundamento",
                        residente.get("codigo").asText(), actividad.get("codigo").asText())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.regla").value("RB-08 datos suficientes"))
                .andExpect(jsonPath("$.umbral").value(0.6)).andExpect(jsonPath("$.texto").isNotEmpty())
                .andExpect(jsonPath("$.registrosOrigen").isArray());
    }

    @Test
    void reconstruyeRegistroConfirmadoYSusPasosComoOrigenDelFundamento() throws Exception {
        insertarRegistroConPasos();
        String token = iniciar("cuidador1");

        mvc.perform(get("/api/residentes/{codigo}/actividades/A-02/fundamento",
                        "00000000-0000-0000-0000-000000000001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.regla").value("RB-08 datos suficientes"))
                .andExpect(jsonPath("$.registrosOrigen[0].codigo").value(REGISTRO_SINTETICO.toString()))
                .andExpect(jsonPath("$.registrosOrigen[0].nivelApoyo").value("VERBAL"))
                .andExpect(jsonPath("$.registrosOrigen[0].pasos.length()").value(6));
    }

    private String iniciar(String usuario) throws Exception {
        String cuerpo = mvc.perform(post("/api/sesiones").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + usuario + "\",\"contrasena\":\"desarrollo123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("token").asText();
    }

    private void insertarParametro(String clave, double valor) {
        jdbc.update("INSERT INTO comun.parametro(id,clave,valor,descripcion) VALUES (gen_random_uuid(),?,?,?) "
                        + "ON CONFLICT (clave) DO UPDATE SET valor=EXCLUDED.valor, vigencia_desde=now()",
                clave, valor, "Parámetro sintético para pruebas OT-09A");
    }

    private void insertarRegistroConPasos() {
        LocalDateTime inicio = LocalDateTime.now(LIMA).minusMinutes(10);
        TipoTurno tipo = TurnoDeclarado.sugerirTipo(inicio);
        TurnoDeclarado turno = new TurnoDeclarado(TURNO_SINTETICO, tipo, CUENTA_SINTETICA, inicio);
        var declaracion = turno.getDeclaradoEn().atZone(LIMA).toOffsetDateTime();
        var inicioTurno = turno.getInicioVentana().atZone(LIMA).toOffsetDateTime();
        var finTurno = turno.getFinVentana().atZone(LIMA).toOffsetDateTime();
        var marca = inicio.atZone(LIMA).toOffsetDateTime();
        jdbc.update("INSERT INTO registro.turno_declarado(id,cuenta_id,tipo,declarado_en,inicio_ventana,fin_ventana) "
                        + "SELECT ?,c.id,?::comun.tipo_turno,?,?,? FROM residentes.cuenta_acceso c WHERE c.codigo='CUI-01'",
                TURNO_SINTETICO, tipo.name(), declaracion, inicioTurno, finTurno);
        jdbc.update("INSERT INTO registro.registro(id,modalidad,expediente_id,actividad_id,plantilla_id,cuenta_id,"
                        + "turno_id,nivel_apoyo_id,fecha_hora,inicio_captura,fin_captura,estado,origen,oportunidad,"
                        + "no_quiso,secuencia_correcta) SELECT ?,'PASOS',e.id,a.id,pp.id,c.id,?,n.id,?,?,?,"
                        + "'CONFIRMADO','TACTIL','SI',FALSE,TRUE FROM residentes.expediente e "
                        + "JOIN catalogo.actividad a ON a.codigo='A-02' "
                        + "JOIN catalogo.plantilla_pasos pp ON pp.actividad_id=a.id AND pp.version=1 "
                        + "JOIN residentes.cuenta_acceso c ON c.codigo='CUI-01' "
                        + "JOIN catalogo.nivel_apoyo n ON n.codigo='VERBAL' "
                        + "JOIN catalogo.escala_apoyo es ON es.id=n.escala_id AND es.vigente=TRUE "
                        + "WHERE e.id=?",
                REGISTRO_SINTETICO, TURNO_SINTETICO, marca, marca, marca,
                UUID.fromString("00000000-0000-0000-0000-000000000001"));
        jdbc.update("INSERT INTO registro.registro_paso(registro_id,plantilla_id,orden,cumplido) "
                        + "SELECT ?,pp.id,p.orden,FALSE FROM catalogo.paso p "
                        + "JOIN catalogo.plantilla_pasos pp ON pp.id=p.plantilla_id "
                        + "JOIN catalogo.actividad a ON a.id=pp.actividad_id "
                        + "WHERE a.codigo='A-02' AND pp.version=1",
                REGISTRO_SINTETICO);
    }
}
