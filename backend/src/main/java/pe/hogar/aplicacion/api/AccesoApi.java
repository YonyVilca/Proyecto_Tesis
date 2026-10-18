package pe.hogar.aplicacion.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.hogar.aplicacion.IAutenticacion;
import pe.hogar.aplicacion.Sesion;
import pe.hogar.aplicacion.ServicioAcceso;
import pe.hogar.aplicacion.RepositorioSesiones;
import pe.hogar.dominio.residentes.IVerificadorContrasena;
import pe.hogar.dominio.residentes.RepositorioCuentas;

/** API HTTP de sesiones, sin estado ni CSRF (RF-01, RF-02, RF-03, DD-53). */
@Configuration
public class AccesoApi {
    @Bean IAutenticacion autenticacion(RepositorioCuentas cuentas, RepositorioSesiones sesiones, IVerificadorContrasena verificador) {
        return new ServicioAcceso(cuentas, sesiones, verificador);
    }

    @Bean SecurityFilterChain seguridad(HttpSecurity http, IAutenticacion autenticacion) throws Exception {
        return http.csrf(csrf -> csrf.disable()).sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.POST, "/api/sesiones").permitAll()
                        .requestMatchers("/api/prueba/coordinacion").hasRole("COORDINADORA").anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, e) -> res.sendError(401)))
                .addFilterBefore(new FiltroToken(autenticacion), AnonymousAuthenticationFilter.class).build();
    }

    static final class FiltroToken extends OncePerRequestFilter {
        private final IAutenticacion autenticacion;
        FiltroToken(IAutenticacion autenticacion) { this.autenticacion = autenticacion; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                autenticacion.obtenerSesion(header.substring(7)).ifPresent(sesion ->
                        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                                sesion, null, List.of(new SimpleGrantedAuthority("ROLE_" + sesion.rol().name())))));
            }
            chain.doFilter(request, response);
        }
    }

    @RestController
    @RequestMapping("/api/sesiones")
    static class SesionesController {
        private final IAutenticacion autenticacion;
        SesionesController(IAutenticacion autenticacion) { this.autenticacion = autenticacion; }
        @PostMapping public org.springframework.http.ResponseEntity<?> iniciar(@RequestBody Credenciales credenciales) {
            return autenticacion.iniciarSesion(credenciales.usuario(), credenciales.contrasena())
                    .<org.springframework.http.ResponseEntity<?>>map(s -> org.springframework.http.ResponseEntity.status(HttpStatus.CREATED)
                            .body(Map.of("token", s.token(), "cuenta", new CuentaRespuesta(s.cuenta().getCodigo(), s.cuenta().getNombreCompleto(), s.rol().name()))))
                    .orElseGet(() -> org.springframework.http.ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("mensaje", "Usuario o contraseña incorrectos")));
        }
        @GetMapping("/actual") public CuentaRespuesta actual(org.springframework.security.core.Authentication auth) {
            Sesion s = (Sesion) auth.getPrincipal();
            return new CuentaRespuesta(s.cuenta().getCodigo(), s.cuenta().getNombreCompleto(), s.rol().name());
        }
        @DeleteMapping("/actual") public org.springframework.http.ResponseEntity<Void> cerrar(org.springframework.security.core.Authentication auth) {
            autenticacion.cerrarSesion(((Sesion) auth.getPrincipal()).token());
            return org.springframework.http.ResponseEntity.noContent().build();
        }
    }
    record Credenciales(String usuario, String contrasena) { }
    record CuentaRespuesta(String codigo, String nombreCompleto, String rol) { }
}
