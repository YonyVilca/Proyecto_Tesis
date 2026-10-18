package pe.hogar.infraestructura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.hogar.aplicacion.ConsultaDatosTurno;
import pe.hogar.aplicacion.DatosTurno;

/** Consulta la descarga del turno sin acceder a atributos de identidad (RF-101, RF-100, DD-43, PD-06). */
@Repository
public class ConsultaDatosTurnoJdbc implements ConsultaDatosTurno {
    private final JdbcTemplate jdbc;
    public ConsultaDatosTurnoJdbc(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public DatosTurno obtener(LocalDate fechaTurno) {
        List<DatosTurno.ExpedienteDatos> expedientes = jdbc.query("SELECT id,nombre_visible,iniciales,color " +
                        "FROM residentes.v_expediente_visible WHERE estado='ACTIVO' ORDER BY id",
                (rs, fila) -> new DatosTurno.ExpedienteDatos(rs.getObject("id", UUID.class),
                        rs.getString("nombre_visible"), rs.getString("iniciales"), rs.getString("color")));

        List<DatosTurno.ActividadResidenteDatos> actividadesResidente = jdbc.query(
                "SELECT ar.expediente_id,a.codigo,ar.estado FROM residentes.actividad_residente ar " +
                        "JOIN residentes.v_expediente_visible e ON e.id=ar.expediente_id " +
                        "JOIN catalogo.actividad a ON a.id=ar.actividad_id " +
                        "WHERE e.estado='ACTIVO' AND ar.vigencia_desde<=? AND (ar.vigencia_hasta IS NULL OR ar.vigencia_hasta>?) " +
                        "ORDER BY ar.expediente_id,a.codigo",
                (rs, fila) -> new DatosTurno.ActividadResidenteDatos(rs.getObject("expediente_id", UUID.class),
                        rs.getString("codigo"), rs.getString("estado")), fechaTurno, fechaTurno);

        Map<String, ActividadBuilder> actividades = new LinkedHashMap<>();
        List<ActividadFila> filasActividad = jdbc.query("SELECT a.codigo,a.nombre,a.modalidad,a.banda,a.secuencia_relevante,pp.version AS plantilla_version, " +
                        "p.orden,p.descripcion,p.critico FROM catalogo.actividad a " +
                        "LEFT JOIN catalogo.plantilla_pasos pp ON pp.actividad_id=a.id " +
                        "AND pp.vigencia_desde<=? AND (pp.vigencia_hasta IS NULL OR pp.vigencia_hasta>?) " +
                        "LEFT JOIN catalogo.paso p ON p.plantilla_id=pp.id WHERE a.vigente=TRUE " +
                        "ORDER BY a.codigo,p.orden",
                (rs, fila) -> {
                    int version = rs.getInt("plantilla_version");
                    Integer versionNulo = rs.wasNull() ? null : version;
                    int orden = rs.getInt("orden");
                    Integer ordenNulo = rs.wasNull() ? null : orden;
                    return new ActividadFila(rs.getString("codigo"), rs.getString("nombre"),
                            rs.getString("modalidad"), rs.getString("banda"), rs.getBoolean("secuencia_relevante"),
                            versionNulo, ordenNulo, rs.getString("descripcion"), rs.getBoolean("critico"));
                }, fechaTurno, fechaTurno);
        for (ActividadFila fila : filasActividad) {
            ActividadBuilder actividad = actividades.computeIfAbsent(fila.codigo(), ignored -> new ActividadBuilder(
                    fila.codigo(), fila.nombre(), fila.modalidad(), fila.banda(), fila.secuenciaRelevante()));
            if (fila.versionPlantilla() != null) {
                actividad.versionPlantilla = fila.versionPlantilla();
                if (fila.orden() != null) actividad.pasos.add(new DatosTurno.PasoDatos(
                        fila.orden(), fila.descripcionPaso(), fila.critico()));
            }
        }
        List<DatosTurno.ActividadDatos> actividadesDto = actividades.values().stream().map(ActividadBuilder::construir).toList();

        List<NivelFila> niveles = jdbc.query("SELECT e.version,e.nombre,n.codigo,n.nombre AS nivel_nombre, " +
                        "n.descripcion_observable,n.peso,n.orden FROM catalogo.escala_apoyo e " +
                        "LEFT JOIN catalogo.nivel_apoyo n ON n.escala_id=e.id " +
                        "WHERE e.vigente=TRUE AND e.vigencia_desde<=? ORDER BY n.orden",
                (rs, fila) -> {
                    int orden = rs.getInt("orden");
                    Integer ordenNulo = rs.wasNull() ? null : orden;
                    return new NivelFila(rs.getInt("version"), rs.getString("nombre"), rs.getString("codigo"),
                            rs.getString("nivel_nombre"), rs.getString("descripcion_observable"),
                            rs.getBigDecimal("peso"), ordenNulo);
                }, fechaTurno);
        if (niveles.isEmpty()) throw new IllegalStateException("No hay escala vigente para la fecha del turno");
        NivelFila primera = niveles.getFirst();
        List<DatosTurno.NivelDatos> nivelesDto = niveles.stream().filter(nivel -> nivel.codigo() != null)
                .map(nivel -> new DatosTurno.NivelDatos(nivel.codigo(), nivel.nivelNombre(), nivel.descripcion(),
                        nivel.peso(), nivel.orden())).toList();
        DatosTurno.EscalaDatos escala = new DatosTurno.EscalaDatos(primera.version(), primera.nombre(), nivelesDto);
        return new DatosTurno(expedientes, actividadesResidente, actividadesDto, escala, List.of());
    }

    private static final class ActividadBuilder {
        private final String codigo;
        private final String nombre;
        private final String modalidad;
        private final String banda;
        private final boolean secuenciaRelevante;
        private final List<DatosTurno.PasoDatos> pasos = new ArrayList<>();
        private Integer versionPlantilla;

        private ActividadBuilder(String codigo, String nombre, String modalidad, String banda, boolean secuenciaRelevante) {
            this.codigo = codigo;
            this.nombre = nombre;
            this.modalidad = modalidad;
            this.banda = banda;
            this.secuenciaRelevante = secuenciaRelevante;
        }

        private DatosTurno.ActividadDatos construir() {
            DatosTurno.PlantillaDatos plantilla = versionPlantilla == null ? null
                    : new DatosTurno.PlantillaDatos(versionPlantilla, pasos);
            return new DatosTurno.ActividadDatos(codigo, nombre, modalidad, banda, secuenciaRelevante, plantilla);
        }
    }

    private record NivelFila(int version, String nombre, String codigo, String nivelNombre,
                             String descripcion, BigDecimal peso, Integer orden) { }
    private record ActividadFila(String codigo, String nombre, String modalidad, String banda,
                                 boolean secuenciaRelevante, Integer versionPlantilla, Integer orden,
                                 String descripcionPaso, boolean critico) { }
}
