package pe.hogar.aplicacion;

import java.time.LocalDateTime;
import java.util.Optional;
import pe.hogar.dominio.residentes.CuentaAcceso;

/** Persistencia de sesiones opacas y revocables (DD-53, PD-03). */
public interface RepositorioSesiones {
    void crear(CuentaAcceso cuenta, String tokenHash);
    Optional<CuentaAcceso> buscarActivaPorTokenHash(String tokenHash);
    void actualizarUltimoUso(String tokenHash, LocalDateTime ultimoUso);
    void revocar(String tokenHash, LocalDateTime revocadaEn);
}
