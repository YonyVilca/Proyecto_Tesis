package pe.hogar.infraestructura;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.hogar.aplicacion.RepositorioExpedientes;
import pe.hogar.dominio.residentes.Expediente;

/** Consulta únicamente los campos autorizados por la vista visible (RF-100, RNF-25, DD-36). */
@Repository
public class RepositorioExpedientesJdbc implements RepositorioExpedientes {
    private final JdbcTemplate jdbc;

    public RepositorioExpedientesJdbc(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<Expediente> obtenerExpedientesVisibles(LocalDate fecha) {
        return jdbc.query("SELECT v.id,v.nombre_visible,v.iniciales,v.color FROM residentes.v_expediente_visible v "
                        + "JOIN residentes.expediente e ON e.id=v.id "
                        + "WHERE e.fecha_ingreso<=? AND (e.fecha_alta IS NULL OR e.fecha_alta>?) "
                        + "ORDER BY lower(v.nombre_visible),v.id",
                (rs, fila) -> new Expediente(rs.getObject("id", UUID.class), rs.getString("nombre_visible"),
                        rs.getString("iniciales"), rs.getString("color").trim()), fecha, fecha);
    }
}
