package pe.hogar.dominio.residentes;

import java.util.Optional;

/** Acceso a cuentas por identificadores de dominio (PD-03). */
public interface RepositorioCuentas {
    Optional<CuentaAcceso> buscarPorUsuario(String usuario);
    Optional<CuentaAcceso> buscarPorCodigo(String codigo);
}
