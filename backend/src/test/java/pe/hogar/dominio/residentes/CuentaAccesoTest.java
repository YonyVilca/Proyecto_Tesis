package pe.hogar.dominio.residentes;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CuentaAccesoTest {
    @Test void autenticaCuentaActivaConContrasenaCorrecta() {
        CuentaAcceso cuenta = cuenta(true);
        assertTrue(cuenta.autenticar("desarrollo123", (contrasena, hash) -> contrasena.equals("desarrollo123") && hash.equals("hash")));
    }
    @Test void rechazaContrasenaIncorrecta() {
        CuentaAcceso cuenta = cuenta(true);
        assertFalse(cuenta.autenticar("incorrecta", (contrasena, hash) -> false));
    }
    @Test void noConsultaVerificadorParaCuentaInactiva() {
        CuentaAcceso cuenta = cuenta(false);
        assertFalse(cuenta.autenticar("desarrollo123", (contrasena, hash) -> fail("No debe verificar una cuenta inactiva")));
    }
    private CuentaAcceso cuenta(boolean activa) {
        return new CuentaAcceso("CUI-01", "Cuidador Uno", "cuidador1", "hash", Rol.CUIDADOR, activa);
    }
}
