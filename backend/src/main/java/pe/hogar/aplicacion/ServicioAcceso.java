package pe.hogar.aplicacion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import pe.hogar.dominio.residentes.CuentaAcceso;
import pe.hogar.dominio.residentes.IVerificadorContrasena;
import pe.hogar.dominio.residentes.RepositorioCuentas;

/** Coordina autenticación y sesiones persistentes con token revocable (RF-01, RF-02, RF-04, DD-53, SD-01). */
public final class ServicioAcceso implements IAutenticacion {
    private final RepositorioCuentas cuentas;
    private final RepositorioSesiones sesiones;
    private final IVerificadorContrasena verificador;
    private final SecureRandom aleatorio = new SecureRandom();

    public ServicioAcceso(RepositorioCuentas cuentas, RepositorioSesiones sesiones, IVerificadorContrasena verificador) {
        this.cuentas = cuentas;
        this.sesiones = sesiones;
        this.verificador = verificador;
    }

    @Override public Optional<Sesion> iniciarSesion(String usuario, String contrasena) {
        Optional<CuentaAcceso> cuenta = cuentas.buscarPorUsuario(usuario);
        if (cuenta.isEmpty() || !cuenta.get().autenticar(contrasena, verificador)) return Optional.empty();
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sesiones.crear(cuenta.get(), hash(token));
        return Optional.of(new Sesion(cuenta.get(), cuenta.get().getRol(), token, null));
    }

    @Override public Optional<Sesion> obtenerSesion(String token) {
        String tokenHash = hash(token);
        return sesiones.buscarActivaPorTokenHash(tokenHash).map(cuenta -> {
            sesiones.actualizarUltimoUso(tokenHash, LocalDateTime.now());
            return new Sesion(cuenta, cuenta.getRol(), token, null);
        });
    }

    @Override public void cerrarSesion(String token) {
        sesiones.revocar(hash(token), LocalDateTime.now());
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
