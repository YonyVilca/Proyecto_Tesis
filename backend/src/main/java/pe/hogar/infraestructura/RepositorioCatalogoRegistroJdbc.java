package pe.hogar.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.hogar.aplicacion.RepositorioCatalogoRegistro;
import pe.hogar.dominio.catalogo.Actividad;
import pe.hogar.dominio.catalogo.BandaFrecuencia;
import pe.hogar.dominio.catalogo.Modalidad;
import pe.hogar.dominio.catalogo.NivelApoyo;
import pe.hogar.dominio.catalogo.Paso;
import pe.hogar.dominio.catalogo.PlantillaPasos;
import pe.hogar.dominio.residentes.Expediente;

/** Carga por código actividad, todas las plantillas y niveles de apoyo (DC-01, RF-65). */
@Repository
public class RepositorioCatalogoRegistroJdbc implements RepositorioCatalogoRegistro {
    private final JdbcTemplate jdbc;
    public RepositorioCatalogoRegistroJdbc(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<Expediente> buscarExpedienteActivo(UUID codigo) {
        List<Expediente> filas = jdbc.query("SELECT id,iniciales FROM residentes.expediente WHERE id=? AND estado='ACTIVO'",
                (rs, n) -> new Expediente(rs.getObject("id", UUID.class), rs.getString("iniciales")), codigo);
        return filas.stream().findFirst();
    }

    @Override public Optional<Actividad> buscarActividad(String codigo) {
        List<ActividadFila> filas = jdbc.query("SELECT a.codigo,a.nombre,e.codigo AS eje,a.modalidad,a.banda,"
                        + "a.frecuencia_esperada,a.secuencia_relevante,pp.version,pp.vigencia_desde,pp.vigencia_hasta,"
                        + "p.orden,p.descripcion,p.critico FROM catalogo.actividad a JOIN catalogo.subgrupo s ON s.id=a.subgrupo_id "
                        + "JOIN catalogo.eje e ON e.id=s.eje_id LEFT JOIN catalogo.plantilla_pasos pp ON pp.actividad_id=a.id "
                        + "LEFT JOIN catalogo.paso p ON p.plantilla_id=pp.id WHERE a.codigo=? ORDER BY pp.version,p.orden",
                this::actividadFila, codigo);
        if (filas.isEmpty()) return Optional.empty();
        ActividadFila base = filas.getFirst();
        List<PlantillaPasos> plantillas = new ArrayList<>();
        int i = 0;
        while (i < filas.size()) {
            if (filas.get(i).version() == null) break;
            ActividadFila plantilla = filas.get(i);
            List<Paso> pasos = new ArrayList<>();
            while (i < filas.size() && filas.get(i).version() != null
                    && filas.get(i).version().equals(plantilla.version())) {
                ActividadFila f = filas.get(i++);
                if (f.orden() != null) pasos.add(new Paso(f.orden(), f.descripcion(), f.critico()));
            }
            plantillas.add(new PlantillaPasos(plantilla.version(), plantilla.desde(), plantilla.hasta(), pasos));
        }
        return Optional.of(new Actividad(base.codigo(), base.nombre(), base.eje(), Modalidad.valueOf(base.modalidad()),
                BandaFrecuencia.valueOf(base.banda()), base.frecuencia(), base.secuencia(), plantillas));
    }

    @Override public Optional<NivelApoyo> buscarNivel(String codigo) {
        List<NivelApoyo> niveles = jdbc.query("SELECT n.codigo,n.nombre,n.peso,n.orden FROM catalogo.nivel_apoyo n "
                        + "JOIN catalogo.escala_apoyo e ON e.id=n.escala_id WHERE n.codigo=? AND e.vigente=TRUE",
                (rs, n) -> new NivelApoyo(rs.getString("codigo"), rs.getString("nombre"),
                        rs.getBigDecimal("peso").doubleValue(), rs.getInt("orden")), codigo);
        return niveles.stream().findFirst();
    }

    private ActividadFila actividadFila(ResultSet rs, int row) throws SQLException {
        int version = rs.getInt("version"); Integer v = rs.wasNull() ? null : version;
        int orden = rs.getInt("orden"); Integer o = rs.wasNull() ? null : orden;
        LocalDate hasta = rs.getObject("vigencia_hasta", LocalDate.class);
        return new ActividadFila(rs.getString("codigo"), rs.getString("nombre"), rs.getString("eje"),
                rs.getString("modalidad"), rs.getString("banda"), rs.getInt("frecuencia_esperada"),
                rs.getBoolean("secuencia_relevante"), v, rs.getObject("vigencia_desde", LocalDate.class),
                hasta, o, rs.getString("descripcion"), rs.getBoolean("critico"));
    }

    private record ActividadFila(String codigo, String nombre, String eje, String modalidad, String banda,
            int frecuencia, boolean secuencia, Integer version, LocalDate desde, LocalDate hasta,
            Integer orden, String descripcion, boolean critico) { }
}
