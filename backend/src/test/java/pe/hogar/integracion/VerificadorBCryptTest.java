package pe.hogar.integracion;

import org.junit.jupiter.api.Test;
import pe.hogar.infraestructura.VerificadorBCrypt;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificadorBCryptTest {
    @Test void aceptaContrasenaDeDesarrollo() {
        assertTrue(new VerificadorBCrypt().verificar("desarrollo123", "$2b$10$xj.WMN3Fqen9Fqx8bW1ucecQHhjPDg.3aJ6d19fDfkb9ft/wONwdS"));
    }
}
