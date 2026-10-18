package pe.hogar.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.hogar.dominio.residentes.CuentaAcceso;
import pe.hogar.dominio.residentes.RepositorioCuentas;
import pe.hogar.dominio.residentes.Rol;

/** Consultas JDBC de cuentas individuales (DC-02, PD-03). */
@Repository
public class RepositorioCuentasJdbc implements RepositorioCuentas {
    private final JdbcTemplate jdbc;
    public RepositorioCuentasJdbc(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Optional<CuentaAcceso> buscarPorUsuario(String usuario) {
        return jdbc.query("SELECT codigo,nombre_completo,usuario,hash_contrasena,rol,activa FROM residentes.cuenta_acceso WHERE usuario=?",
                this::mapear, usuario).stream().findFirst();
    }
    @Override public Optional<CuentaAcceso> buscarPorCodigo(String codigo) {
        return jdbc.query("SELECT codigo,nombre_completo,usuario,hash_contrasena,rol,activa FROM residentes.cuenta_acceso WHERE codigo=?",
                this::mapear, codigo).stream().findFirst();
    }
    private CuentaAcceso mapear(ResultSet rs, int fila) throws SQLException {
        return new CuentaAcceso(rs.getString("codigo"), rs.getString("nombre_completo"), rs.getString("usuario"),
                rs.getString("hash_contrasena"), Rol.valueOf(rs.getString("rol")), rs.getBoolean("activa"));
    }
}
