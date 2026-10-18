package pe.hogar.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.hogar.aplicacion.RepositorioTurnos;
import pe.hogar.dominio.registro.TipoTurno;
import pe.hogar.dominio.registro.TurnoDeclarado;
import pe.hogar.dominio.residentes.CuentaAcceso;
import pe.hogar.dominio.residentes.Rol;

/** Persistencia JDBC de turnos y encadenamientos (DC-03, PD-03, DD-34). */
@Repository
public class RepositorioTurnosJdbc implements RepositorioTurnos {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final JdbcTemplate jdbc;
    public RepositorioTurnosJdbc(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void bloquearDeclaracion(UUID codigo, CuentaAcceso cuenta) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class,
                codigo.toString());
    }

    @Override
    public Optional<TurnoDeclarado> buscarPorCodigo(UUID codigo) {
        List<TurnoDeclarado> encontrados = jdbc.query("SELECT t.id,t.tipo,t.declarado_en,c.codigo,c.nombre_completo," +
                        "c.usuario,c.hash_contrasena,c.rol,c.activa FROM registro.turno_declarado t " +
                        "JOIN residentes.cuenta_acceso c ON c.id=t.cuenta_id WHERE t.id=?",
                this::mapearTurno, codigo);
        if (encontrados.isEmpty()) return Optional.empty();
        TurnoDeclarado turno = encontrados.getFirst();
        jdbc.query("SELECT tipo FROM registro.turno_encadenado WHERE turno_id=? ORDER BY orden",
                rs -> { while (rs.next()) turno.encadenar(TipoTurno.valueOf(rs.getString("tipo"))); }, codigo);
        return Optional.of(turno);
    }

    @Override
    public boolean existeSolapamiento(UUID codigo, CuentaAcceso cuenta, LocalDateTime inicio, LocalDateTime fin) {
        Boolean existe = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM registro.turno_declarado t " +
                        "JOIN residentes.cuenta_acceso c ON c.id=t.cuenta_id WHERE c.codigo=? AND t.id<>? " +
                        "AND tstzrange(t.inicio_ventana,t.fin_ventana,'[)') && tstzrange(?,?,'[)'))",
                Boolean.class, cuenta.getCodigo(), codigo, enLima(inicio), enLima(fin));
        return Boolean.TRUE.equals(existe);
    }

    @Override
    public void insertar(TurnoDeclarado turno) {
        jdbc.update("INSERT INTO registro.turno_declarado(id,cuenta_id,tipo,declarado_en,inicio_ventana,fin_ventana) " +
                        "SELECT ?,c.id,?::comun.tipo_turno,?,?,? FROM residentes.cuenta_acceso c WHERE c.codigo=?",
                turno.getCodigo(), turno.getTipo().name(), enLima(turno.getDeclaradoEn()),
                enLima(turno.getInicioVentana()), enLima(turno.getFinVentana()), turno.getCuenta().getCodigo());
        insertarEncadenados(turno.getCodigo(), 1, turno.getEncadenados());
    }

    @Override
    public void agregarEncadenados(UUID codigo, int siguienteOrden, List<TipoTurno> nuevos, LocalDateTime finVentana) {
        jdbc.update("UPDATE registro.turno_declarado SET fin_ventana=? WHERE id=?", enLima(finVentana), codigo);
        insertarEncadenados(codigo, siguienteOrden, nuevos);
    }

    private void insertarEncadenados(UUID codigo, int ordenInicial, List<TipoTurno> encadenados) {
        for (int i = 0; i < encadenados.size(); i++) {
            jdbc.update("INSERT INTO registro.turno_encadenado(turno_id,orden,tipo) VALUES (?, ?, ?::comun.tipo_turno)",
                    codigo, ordenInicial + i, encadenados.get(i).name());
        }
    }

    private TurnoDeclarado mapearTurno(ResultSet rs, int fila) throws SQLException {
        CuentaAcceso cuenta = new CuentaAcceso(rs.getString("codigo"), rs.getString("nombre_completo"),
                rs.getString("usuario"), rs.getString("hash_contrasena"), Rol.valueOf(rs.getString("rol")),
                rs.getBoolean("activa"));
        return new TurnoDeclarado(rs.getObject("id", UUID.class), TipoTurno.valueOf(rs.getString("tipo")), cuenta,
                enLima(rs.getObject("declarado_en", OffsetDateTime.class)));
    }

    private OffsetDateTime enLima(LocalDateTime fechaHora) { return fechaHora.atZone(LIMA).toOffsetDateTime(); }
    private LocalDateTime enLima(OffsetDateTime fechaHora) { return fechaHora.atZoneSameInstant(LIMA).toLocalDateTime(); }
}
