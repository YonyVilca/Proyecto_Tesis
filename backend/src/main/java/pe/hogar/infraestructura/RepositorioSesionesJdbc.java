package pe.hogar.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.hogar.aplicacion.RepositorioSesiones;
import pe.hogar.dominio.residentes.CuentaAcceso;
import pe.hogar.dominio.residentes.Rol;

/** Persistencia JDBC de sesiones y comprobación de cuenta activa (DD-53, PD-03). */
@Repository
public class RepositorioSesionesJdbc implements RepositorioSesiones {
    private final JdbcTemplate jdbc;
    public RepositorioSesionesJdbc(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void crear(CuentaAcceso cuenta, String tokenHash) {
        jdbc.update("INSERT INTO residentes.sesion(cuenta_id,token_hash) SELECT id,? FROM residentes.cuenta_acceso WHERE codigo=?", tokenHash, cuenta.getCodigo());
    }
    @Override public Optional<CuentaAcceso> buscarActivaPorTokenHash(String tokenHash) {
        return jdbc.query("SELECT c.codigo,c.nombre_completo,c.usuario,c.hash_contrasena,c.rol,c.activa " +
                "FROM residentes.sesion s JOIN residentes.cuenta_acceso c ON c.id=s.cuenta_id " +
                "WHERE s.token_hash=? AND s.revocada_en IS NULL AND c.activa=TRUE", this::mapear, tokenHash).stream().findFirst();
    }
    @Override public void actualizarUltimoUso(String tokenHash) {
        jdbc.update("UPDATE residentes.sesion SET ultimo_uso=now() WHERE token_hash=? AND revocada_en IS NULL", tokenHash);
    }
    @Override public void revocar(String tokenHash) {
        jdbc.update("UPDATE residentes.sesion SET revocada_en=now() WHERE token_hash=? AND revocada_en IS NULL", tokenHash);
    }
    private CuentaAcceso mapear(ResultSet rs, int fila) throws SQLException {
        return new CuentaAcceso(rs.getString("codigo"), rs.getString("nombre_completo"), rs.getString("usuario"),
                rs.getString("hash_contrasena"), Rol.valueOf(rs.getString("rol")), rs.getBoolean("activa"));
    }
}
