package pe.hogar.aplicacion;

import pe.hogar.dominio.residentes.CuentaAcceso;
import pe.hogar.dominio.residentes.Rol;
import java.time.LocalDateTime;

/** Sesión de acceso sin expiración temporal (RF-02, DD-53). */
public record Sesion(CuentaAcceso cuenta, Rol rol, String token, LocalDateTime expira) {
    public Sesion {
        expira = null;
    }
}
