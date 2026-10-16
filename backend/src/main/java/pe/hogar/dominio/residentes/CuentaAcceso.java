package pe.hogar.dominio.residentes;

import java.util.Objects;
import java.util.UUID;

/** Cuenta individual de un cuidador o de la coordinadora (RF-01). Solo los atributos que usa el dominio. */
public final class CuentaAcceso {

    private final UUID codigo;
    private final String usuario;
    private final Rol rol;

    public CuentaAcceso(UUID codigo, String usuario, Rol rol) {
        this.codigo = Objects.requireNonNull(codigo);
        this.usuario = Objects.requireNonNull(usuario);
        this.rol = Objects.requireNonNull(rol);
    }

    public UUID getCodigo() { return codigo; }
    public String getUsuario() { return usuario; }
    public Rol getRol() { return rol; }
}
