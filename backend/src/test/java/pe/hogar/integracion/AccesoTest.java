package pe.hogar.integracion;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AccesoTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test void iniciaSesionAccedeRevocaYAlmacenaSoloHash() throws Exception {
        String token = iniciar("cuidador1", "desarrollo123");
        String hash = jdbc.queryForObject("SELECT s.token_hash FROM residentes.sesion s JOIN residentes.cuenta_acceso c " +
                "ON c.id=s.cuenta_id WHERE c.usuario='cuidador1' ORDER BY s.creada_en DESC LIMIT 1", String.class);
        assertNotEquals(token, hash);
        mvc.perform(get("/api/sesiones/actual").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.codigo").value("CUI-01"));
        mvc.perform(delete("/api/sesiones/actual").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/sesiones/actual").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test void credencialesIncorrectasYUsuarioInexistenteCompartenRespuesta() throws Exception {
        MvcResult incorrecta = mvc.perform(post("/api/sesiones").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":\"cuidador1\",\"contrasena\":\"mal\"}")).andExpect(status().isUnauthorized()).andReturn();
        MvcResult inexistente = mvc.perform(post("/api/sesiones").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":\"fantasma\",\"contrasena\":\"mal\"}")).andExpect(status().isUnauthorized()).andReturn();
        assertEquals(incorrecta.getResponse().getContentAsString(), inexistente.getResponse().getContentAsString());
        assertEquals("{\"mensaje\":\"Usuario o contraseña incorrectos\"}", incorrecta.getResponse().getContentAsString());
    }

    @Test void cuentaDesactivadaInvalidaSesionExistenteYNoPermiteIniciar() throws Exception {
        String token = iniciar("cuidador2", "desarrollo123");
        jdbc.update("UPDATE residentes.cuenta_acceso SET activa=false WHERE usuario='cuidador2'");
        mvc.perform(get("/api/sesiones/actual").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/sesiones").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":\"cuidador2\",\"contrasena\":\"desarrollo123\"}")).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE residentes.cuenta_acceso SET activa=true WHERE usuario='cuidador2'");
    }

    private String iniciar(String usuario, String contrasena) throws Exception {
        String response = mvc.perform(post("/api/sesiones").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":\"" + usuario + "\",\"contrasena\":\"" + contrasena + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("token").asText();
    }
}
