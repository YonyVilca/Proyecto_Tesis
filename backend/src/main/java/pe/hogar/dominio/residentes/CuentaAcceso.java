package pe.hogar.dominio.residentes;

import java.util.Objects;

/** Cuenta individual con estado de acceso y credenciales (RF-01, RF-04, DC-02). */
public final class CuentaAcceso {
    private final String codigo;
    private final String nombreCompleto;
    private final String usuario;
    private final String hashContrasena;
    private final Rol rol;
    private final boolean activa;

    public CuentaAcceso(String codigo, String nombreCompleto, String usuario, String hashContrasena,
                        Rol rol, boolean activa) {
        this.codigo = Objects.requireNonNull(codigo);
        this.nombreCompleto = Objects.requireNonNull(nombreCompleto);
        this.usuario = Objects.requireNonNull(usuario);
        this.hashContrasena = Objects.requireNonNull(hashContrasena);
        this.rol = Objects.requireNonNull(rol);
        this.activa = activa;
    }

    /** Valida la contraseña mediante un verificador externo y bloquea cuentas desactivadas (RF-04, RNF-23). */
    public boolean autenticar(String contrasena, IVerificadorContrasena verificador) {
        return activa && verificador.verificar(contrasena, hashContrasena);
    }

    public String getCodigo() { return codigo; }
    public String getNombreCompleto() { return nombreCompleto; }
    public String getUsuario() { return usuario; }
    public Rol getRol() { return rol; }
    public boolean isActiva() { return activa; }
}
