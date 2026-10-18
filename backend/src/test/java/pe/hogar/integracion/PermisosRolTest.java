package pe.hogar.integracion;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Comprueba la autorización por rol de RF-03. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(PermisosRolTest.ControladorPrueba.class)
class PermisosRolTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired MockMvc mvc;

    @Test void soloCoordinadoraAccedeAlRecursoProtegido() throws Exception {
        String tokenCuidador = iniciar("cuidador1");
        String tokenCoordinadora = iniciar("coordinadora");
        mvc.perform(get("/api/prueba/coordinacion").header("Authorization", "Bearer " + tokenCuidador))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/prueba/coordinacion").header("Authorization", "Bearer " + tokenCoordinadora))
                .andExpect(status().isOk());
    }

    private String iniciar(String usuario) throws Exception {
        String response = mvc.perform(post("/api/sesiones").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + usuario + "\",\"contrasena\":\"desarrollo123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(response).get("token").asText();
    }

    @RestController
    static class ControladorPrueba {
        @PreAuthorize("hasRole('COORDINADORA')")
        @GetMapping("/api/prueba/coordinacion") String coordinacion() { return "ok"; }
    }
}
