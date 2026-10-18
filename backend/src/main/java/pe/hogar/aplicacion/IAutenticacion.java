package pe.hogar.aplicacion;

import java.util.Optional;

/** Operaciones del ciclo de vida de una sesión (RF-01, RF-02, DD-53). */
public interface IAutenticacion {
    Optional<Sesion> iniciarSesion(String usuario, String contrasena);
    Optional<Sesion> obtenerSesion(String token);
    void cerrarSesion(String token);
}
