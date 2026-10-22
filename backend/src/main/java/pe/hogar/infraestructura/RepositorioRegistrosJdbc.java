package pe.hogar.infraestructura;

import java.util.UUID;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.hogar.aplicacion.RepositorioRegistros;
import pe.hogar.dominio.registro.Registro;
import pe.hogar.dominio.registro.RegistroPorPasos;
import pe.hogar.dominio.registro.CampoPrecarga;

/** Guarda cada registro y sus pasos/campos modificados en la transacción de recepción (DC-03, PD-03). */
@Repository
public class RepositorioRegistrosJdbc implements RepositorioRegistros {
    private final JdbcTemplate jdbc;
    public RepositorioRegistrosJdbc(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public boolean existe(UUID codigo) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class, codigo.toString());
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM registro.registro WHERE id=?)",
                Boolean.class, codigo));
    }

    @Override public void guardar(UUID codigoRecibido, Registro r, Set<CampoPrecarga> camposModificados) {
        if (!(r instanceof RegistroPorPasos rp)) throw new IllegalArgumentException("Modalidad no implementada en esta recepción");
        jdbc.update("INSERT INTO registro.registro(id,modalidad,expediente_id,actividad_id,plantilla_id,cuenta_id,turno_id,"
                        + "nivel_apoyo_id,fecha_hora,inicio_captura,fin_captura,estado,origen,oportunidad,motivo_sin_oportunidad,"
                        + "motivo_otro,no_quiso,nota,secuencia_correcta,participo) VALUES (?, 'PASOS', ?, "
                        + "(SELECT id FROM catalogo.actividad WHERE codigo=?), "
                        + "(SELECT pp.id FROM catalogo.plantilla_pasos pp JOIN catalogo.actividad a ON a.id=pp.actividad_id "
                        + "WHERE a.codigo=? AND pp.version=?), (SELECT id FROM residentes.cuenta_acceso WHERE codigo=?), ?, "
                        + "(SELECT n.id FROM catalogo.nivel_apoyo n WHERE n.codigo=? AND n.escala_id=(SELECT id FROM catalogo.escala_apoyo WHERE vigente=TRUE)), "
                        + "?,?,?,'CONFIRMADO',?::comun.origen_registro,?::comun.oportunidad,?::comun.motivo_sin_oportunidad,?,?,?, ?,NULL)",
                codigoRecibido, r.getExpediente().getCodigo(), r.getActividad().getCodigo(), r.getActividad().getCodigo(),
                rp.getVersionPlantilla(), r.getAutor().getCodigo(), r.getTurno().getCodigo(),
                r.getNivel() == null ? null : r.getNivel().codigo(), r.getFechaHora(), r.getInicioCaptura(), r.getFinCaptura(),
                r.getOrigen().name(), r.getOportunidad().name(),
                r.getMotivoSinOportunidad() == null ? null : r.getMotivoSinOportunidad().name(), r.getMotivoOtro(),
                r.isNoQuiso(), r.getNota(), rp.getSecuenciaCorrecta());
        if (r.getOportunidad() == pe.hogar.dominio.registro.Oportunidad.SI && !r.isNoQuiso()) {
            for (var paso : rp.getPasos()) jdbc.update("INSERT INTO registro.registro_paso(registro_id,plantilla_id,orden,cumplido) "
                    + "SELECT ?,pp.id,?,? FROM catalogo.plantilla_pasos pp JOIN catalogo.actividad a ON a.id=pp.actividad_id "
                    + "WHERE a.codigo=? AND pp.version=?", codigoRecibido, paso.getOrden(), paso.isCumplido(),
                    r.getActividad().getCodigo(), rp.getVersionPlantilla());
        }
        for (var campo : camposModificados) jdbc.update("INSERT INTO registro.registro_campo_modificado(registro_id,campo) "
                + "VALUES (?,?::comun.campo_precarga)", codigoRecibido, campo.name());
    }
}
