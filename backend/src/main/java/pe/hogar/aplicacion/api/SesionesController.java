package pe.hogar.aplicacion.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.hogar.aplicacion.IAutenticacion;
import pe.hogar.aplicacion.Sesion;

/** Endpoints públicos y protegidos para gestionar la sesión (RF-01, RF-02, DD-53). */
@RestController
@RequestMapping("/api/sesiones")
public class SesionesController {
    private final IAutenticacion autenticacion;

    public SesionesController(IAutenticacion autenticacion) { this.autenticacion = autenticacion; }

    @PostMapping
    public ResponseEntity<?> iniciar(@RequestBody Credenciales credenciales) {
        return autenticacion.iniciarSesion(credenciales.usuario(), credenciales.contrasena())
                .<ResponseEntity<?>>map(s -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(Map.of("token", s.token(), "cuenta",
                                new CuentaRespuesta(s.cuenta().getCodigo(), s.cuenta().getNombreCompleto(), s.rol().name()))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("mensaje", "Usuario o contraseña incorrectos")));
    }

    @GetMapping("/actual")
    public CuentaRespuesta actual(Authentication auth) {
        Sesion sesion = (Sesion) auth.getPrincipal();
        return new CuentaRespuesta(sesion.cuenta().getCodigo(), sesion.cuenta().getNombreCompleto(), sesion.rol().name());
    }

    @DeleteMapping("/actual")
    public ResponseEntity<Void> cerrar(Authentication auth) {
        autenticacion.cerrarSesion(((Sesion) auth.getPrincipal()).token());
        return ResponseEntity.noContent().build();
    }

    record Credenciales(String usuario, String contrasena) { }
    record CuentaRespuesta(String codigo, String nombreCompleto, String rol) { }
}
