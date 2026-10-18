package pe.hogar.aplicacion.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import pe.hogar.aplicacion.IAutenticacion;
import pe.hogar.aplicacion.RepositorioSesiones;
import pe.hogar.aplicacion.ServicioAcceso;
import pe.hogar.dominio.residentes.IVerificadorContrasena;
import pe.hogar.dominio.residentes.RepositorioCuentas;

/** Configura autenticación sin estado y el ciclo de vida de sesiones (RF-01, RF-02, RF-03, DD-53). */
@Configuration
@EnableMethodSecurity
public class ConfiguracionSeguridad {
    @Bean
    IAutenticacion autenticacion(RepositorioCuentas cuentas, RepositorioSesiones sesiones,
                                 IVerificadorContrasena verificador) {
        return new ServicioAcceso(cuentas, sesiones, verificador);
    }

    @Bean
    SecurityFilterChain seguridad(HttpSecurity http, IAutenticacion autenticacion) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.POST, "/api/sesiones").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, e) -> res.sendError(401)))
                .addFilterBefore(new FiltroToken(autenticacion), AnonymousAuthenticationFilter.class).build();
    }
}
