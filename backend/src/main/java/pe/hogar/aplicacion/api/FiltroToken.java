package pe.hogar.aplicacion.api;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.hogar.aplicacion.IAutenticacion;

/** Resuelve el token opaco Bearer y asigna la autoridad de su cuenta (DD-53, RF-03). */
public class FiltroToken extends OncePerRequestFilter {
    private final IAutenticacion autenticacion;

    public FiltroToken(IAutenticacion autenticacion) { this.autenticacion = autenticacion; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
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
