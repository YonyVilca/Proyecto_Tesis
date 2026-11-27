package pe.hogar.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.hogar.aplicacion.RepositorioCatalogoRegistro;
import pe.hogar.aplicacion.RepositorioRegistrosAnalisis;
import pe.hogar.dominio.catalogo.NivelApoyo;
import pe.hogar.dominio.registro.FabricaRegistro;
import pe.hogar.dominio.registro.MotivoSinOportunidad;
import pe.hogar.dominio.registro.Oportunidad;
import pe.hogar.dominio.registro.OrigenRegistro;
import pe.hogar.dominio.registro.Registro;
import pe.hogar.dominio.registro.TipoTurno;
import pe.hogar.dominio.registro.TurnoDeclarado;
import pe.hogar.dominio.residentes.CuentaAcceso;
import pe.hogar.dominio.residentes.Expediente;
import pe.hogar.dominio.residentes.Rol;

/** Reconstruye la serie confirmada sin cargar anulados ni consultar el esquema de identidad (RF-75, RF-100). */
@Repository
public class RepositorioRegistrosAnalisisJdbc implements RepositorioRegistrosAnalisis {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final CuentaAcceso CUENTA_ANALISIS = new CuentaAcceso(
            "ANALISIS", "Servicio de análisis", "analisis", "", Rol.COORDINADORA, true);

    private final JdbcTemplate jdbc;
    private final RepositorioCatalogoRegistro catalogo;
    private final FabricaRegistro fabrica = new FabricaRegistro();

    public RepositorioRegistrosAnalisisJdbc(JdbcTemplate jdbc, RepositorioCatalogoRegistro catalogo) {
        this.jdbc = jdbc;
        this.catalogo = catalogo;
    }

    @Override
    public List<Registro> obtenerConfirmados(UUID expediente, LocalDate hasta) {
        List<RegistroFila> filas = jdbc.query("SELECT r.id,r.expediente_id,e.iniciales,a.codigo AS actividad,"
                        + "pp.version,r.turno_id,t.tipo AS turno_tipo,t.declarado_en,"
                        + "r.fecha_hora,r.inicio_captura,r.fin_captura,r.origen,r.oportunidad,"
                        + "r.motivo_sin_oportunidad,r.motivo_otro,r.no_quiso,n.codigo AS nivel,"
                        + "n.nombre AS nivel_nombre,n.peso AS nivel_peso,n.orden AS nivel_orden,"
                        + "r.secuencia_correcta,r.participo FROM registro.registro r "
                        + "JOIN residentes.expediente e ON e.id=r.expediente_id "
                        + "JOIN catalogo.actividad a ON a.id=r.actividad_id "
                        + "LEFT JOIN catalogo.plantilla_pasos pp ON pp.id=r.plantilla_id "
                        + "JOIN registro.turno_declarado t ON t.id=r.turno_id "
                        + "LEFT JOIN catalogo.nivel_apoyo n ON n.id=r.nivel_apoyo_id "
                        + "WHERE r.expediente_id=? AND r.estado='CONFIRMADO' AND r.fecha_hora<? "
                        + "ORDER BY r.fecha_hora,r.id",
                this::mapearFila, expediente, hasta.plusDays(1).atStartOfDay(LIMA).toOffsetDateTime());

        List<Registro> resultado = new ArrayList<>(filas.size());
        for (RegistroFila fila : filas) resultado.add(reconstruir(fila));
        return List.copyOf(resultado);
    }

    private Registro reconstruir(RegistroFila fila) {
        var actividad = catalogo.buscarActividad(fila.actividad())
                .orElseThrow(() -> new IllegalStateException("No existe la actividad del registro"));
        Expediente residente = catalogo.buscarExpedienteActivo(fila.expediente())
                .orElseGet(() -> new Expediente(fila.expediente(), fila.iniciales()));
        TurnoDeclarado turno = new TurnoDeclarado(fila.turno(), TipoTurno.valueOf(fila.turnoTipo()),
                CUENTA_ANALISIS, fila.declaradoEn());
        jdbc.query("SELECT tipo FROM registro.turno_encadenado WHERE turno_id=? ORDER BY orden",
                rs -> { while (rs.next()) turno.encadenar(TipoTurno.valueOf(rs.getString("tipo"))); }, fila.turno());
        List<Boolean> pasos = jdbc.query("SELECT cumplido FROM registro.registro_paso WHERE registro_id=? ORDER BY orden",
                (rs, row) -> rs.getBoolean("cumplido"), fila.codigo());
        return fabrica.reconstruir(fila.codigo(), actividad, residente, turno, fila.inicioCaptura(),
                OrigenRegistro.valueOf(fila.origen()), fila.versionPlantilla(), fila.fechaHora(), fila.finCaptura(),
                Oportunidad.valueOf(fila.oportunidad()), fila.motivo() == null ? null
                        : MotivoSinOportunidad.valueOf(fila.motivo()), fila.motivoOtro(), fila.noQuiso(), fila.nivel(), pasos,
                fila.secuencia(), fila.participo(), false, null);
    }

    private RegistroFila mapearFila(ResultSet rs, int row) throws SQLException {
        Integer version = (Integer) rs.getObject("version");
        return new RegistroFila(rs.getObject("id", UUID.class), rs.getObject("expediente_id", UUID.class),
                rs.getString("iniciales"), rs.getString("actividad"), version == null ? 0 : version,
                rs.getObject("turno_id", UUID.class), rs.getString("turno_tipo"),
                lima(rs.getObject("declarado_en", OffsetDateTime.class)),
                lima(rs.getObject("fecha_hora", OffsetDateTime.class)),
                lima(rs.getObject("inicio_captura", OffsetDateTime.class)),
                lima(rs.getObject("fin_captura", OffsetDateTime.class)), rs.getString("origen"),
                rs.getString("oportunidad"), rs.getString("motivo_sin_oportunidad"), rs.getString("motivo_otro"),
                rs.getBoolean("no_quiso"), nivel(rs), rs.getObject("secuencia_correcta", Boolean.class),
                rs.getObject("participo", Boolean.class));
    }

    private NivelApoyo nivel(ResultSet rs) throws SQLException {
        String codigo = rs.getString("nivel");
        return codigo == null ? null : new NivelApoyo(codigo, rs.getString("nivel_nombre"),
                rs.getBigDecimal("nivel_peso").doubleValue(), rs.getInt("nivel_orden"));
    }

    private LocalDateTime lima(OffsetDateTime fecha) { return fecha.atZoneSameInstant(LIMA).toLocalDateTime(); }

    private record RegistroFila(UUID codigo, UUID expediente, String iniciales, String actividad,
            int versionPlantilla, UUID turno, String turnoTipo, LocalDateTime declaradoEn, LocalDateTime fechaHora,
            LocalDateTime inicioCaptura, LocalDateTime finCaptura, String origen, String oportunidad, String motivo,
            String motivoOtro, boolean noQuiso, NivelApoyo nivel, Boolean secuencia, Boolean participo) { }
}
