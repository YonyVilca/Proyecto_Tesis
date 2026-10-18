package pe.hogar.aplicacion;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pe.hogar.dominio.residentes.CuentaAcceso;
import pe.hogar.dominio.residentes.RepositorioCuentas;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServicioAccesoTest {
    @Test void verificaContrasenaAunqueUsuarioNoExista() {
        boolean[] verificadorInvocado = {false};
        String[] hashVerificado = {null};
        RepositorioCuentas cuentas = new RepositorioCuentas() {
            @Override public Optional<CuentaAcceso> buscarPorUsuario(String usuario) { return Optional.empty(); }
            @Override public Optional<CuentaAcceso> buscarPorCodigo(String codigo) { return Optional.empty(); }
        };
        RepositorioSesiones sesiones = new RepositorioSesiones() {
            @Override public void crear(CuentaAcceso cuenta, String tokenHash) { throw new AssertionError("No debe crear sesión"); }
            @Override public Optional<CuentaAcceso> buscarActivaPorTokenHash(String tokenHash) { return Optional.empty(); }
            @Override public void actualizarUltimoUso(String tokenHash) { }
            @Override public void revocar(String tokenHash) { }
        };
        ServicioAcceso servicio = new ServicioAcceso(cuentas, sesiones, (contrasena, hash) -> {
            verificadorInvocado[0] = true;
            hashVerificado[0] = hash;
            return false;
        });

        assertTrue(servicio.iniciarSesion("inexistente", "desarrollo123").isEmpty());
        assertTrue(verificadorInvocado[0]);
        assertTrue(hashVerificado[0].startsWith("$2b$10$"));
        assertFalse(hashVerificado[0].isBlank());
    }
}
