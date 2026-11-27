package pe.hogar.infraestructura;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.hogar.aplicacion.RepositorioCatalogoRegistro;
import pe.hogar.aplicacion.RepositorioPlanResidente;
import pe.hogar.dominio.residentes.ActividadResidente;
import pe.hogar.dominio.residentes.EstadoActividadResidente;

/** Carga los tramos del plan del residente que se cruzan con el período (RB-04, DC-04). */
@Repository
public class RepositorioPlanResidenteJdbc implements RepositorioPlanResidente {
    private final JdbcTemplate jdbc;
    private final RepositorioCatalogoRegistro catalogo;

    public RepositorioPlanResidenteJdbc(JdbcTemplate jdbc, RepositorioCatalogoRegistro catalogo) {
        this.jdbc = jdbc;
        this.catalogo = catalogo;
    }

    @Override
    public List<ActividadResidente> obtenerActividades(UUID expediente, LocalDate desde, LocalDate hasta) {
        List<ActividadFila> filas = jdbc.query("SELECT ar.id,a.codigo AS actividad,ar.estado,ar.vigencia_desde,ar.vigencia_hasta "
                        + "FROM residentes.actividad_residente ar JOIN catalogo.actividad a ON a.id=ar.actividad_id "
                        + "WHERE ar.expediente_id=? AND ar.vigencia_desde<=? "
                        + "AND (ar.vigencia_hasta IS NULL OR ar.vigencia_hasta>?) "
                        + "ORDER BY a.codigo,ar.vigencia_desde",
                (rs, fila) -> new ActividadFila(rs.getObject("id", UUID.class), rs.getString("actividad"),
                        rs.getString("estado"), rs.getObject("vigencia_desde", LocalDate.class),
                        rs.getObject("vigencia_hasta", LocalDate.class)), expediente, hasta, desde);
        return filas.stream().map(this::mapear).toList();
    }

    private ActividadResidente mapear(ActividadFila fila) {
        var actividad = catalogo.buscarActividad(fila.actividad())
                .orElseThrow(() -> new IllegalStateException("No existe la actividad del plan"));
        return new ActividadResidente(fila.codigo(), actividad, EstadoActividadResidente.valueOf(fila.estado()),
                fila.desde(), fila.hasta() == null ? null : fila.hasta().minusDays(1));
    }

    /** La base almacena fin exclusivo; el agregado de dominio expresa la última fecha inclusiva. */
    private record ActividadFila(UUID codigo, String actividad, String estado, LocalDate desde, LocalDate hasta) { }
}
