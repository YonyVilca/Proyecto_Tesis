package pe.hogar.infraestructura;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import pe.hogar.dominio.residentes.IVerificadorContrasena;

/** Verificación BCrypt con sal integrada al hash (RNF-23, DD-54). */
@Component
public class VerificadorBCrypt implements IVerificadorContrasena {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    @Override public boolean verificar(String contrasena, String hash) { return encoder.matches(contrasena, hash); }
}
