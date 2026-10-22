package pe.hogar.aplicacion;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.hogar.dominio.registro.TipoTurno;
import pe.hogar.dominio.registro.TurnoDeclarado;
import pe.hogar.dominio.registro.FabricaRegistro;
import pe.hogar.dominio.registro.Registro;
import pe.hogar.dominio.registro.RegistroPorPasos;
import pe.hogar.dominio.registro.Oportunidad;
import pe.hogar.dominio.catalogo.NivelApoyo;
import pe.hogar.dominio.residentes.CuentaAcceso;

/** Recepción idempotente de turnos y lectura de sus datos de captura (RF-12, RF-90, RF-101, RB-11, D-66, DD-34). */
@Service
@Transactional
public class ServicioRegistro implements IRegistro {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final int TOLERANCIA_FUTURA_MINUTOS = 15;

    private final RepositorioTurnos turnos;
    private final ConsultaDatosTurno consultaDatos;
    private final RepositorioRegistros registros;
    private final RepositorioCatalogoRegistro catalogo;
    private final FabricaRegistro fabrica = new FabricaRegistro();

    public ServicioRegistro(RepositorioTurnos turnos, ConsultaDatosTurno consultaDatos,
                            RepositorioRegistros registros, RepositorioCatalogoRegistro catalogo) {
        this.turnos = turnos;
        this.consultaDatos = consultaDatos;
        this.registros = registros;
        this.catalogo = catalogo;
    }

    /** Recepción idempotente conforme a SD-04; el contenido del reenvío no altera el resultado (RF-32). */
    public ResultadoRecepcion recibirRegistro(RegistroRecibido recibido, CuentaAcceso autor) {
        if (recibido == null || recibido.codigo() == null || recibido.inicioCaptura() == null
                || recibido.finCaptura() == null || recibido.origen() == null || recibido.expediente() == null
                || recibido.turno() == null || recibido.actividad() == null)
            return ResultadoRecepcion.RECHAZADO_VALIDACION;
        if (registros.existe(recibido.codigo())) return ResultadoRecepcion.DUPLICADO;
        TurnoDeclarado turno = turnos.buscarPorCodigo(recibido.turno()).orElse(null);
        if (turno == null || !turno.getCuenta().getCodigo().equals(autor.getCodigo()))
            return ResultadoRecepcion.RECHAZADO_SIN_TURNO;
        LocalDateTime ahora = LocalDateTime.now(LIMA);
        if (!turno.habilitaRegistro() || !turno.contieneMomento(recibido.inicioCaptura(), 15)
                || recibido.inicioCaptura().isAfter(ahora)) return ResultadoRecepcion.RECHAZADO_FUERA_DE_VENTANA;
        var expediente = catalogo.buscarExpedienteActivo(recibido.expediente()).orElse(null);
        var actividad = catalogo.buscarActividad(recibido.actividad()).orElse(null);
        NivelApoyo nivel = recibido.nivel() == null ? null : catalogo.buscarNivel(recibido.nivel()).orElse(null);
        if (expediente == null || actividad == null || (recibido.nivel() != null && nivel == null))
            return ResultadoRecepcion.RECHAZADO_VALIDACION;
        try {
            Registro registro = fabrica.reconstruir(recibido.codigo(), actividad, expediente, turno, recibido.inicioCaptura(),
                    recibido.origen(), recibido.versionPlantilla());
            Oportunidad oportunidad = recibido.oportunidad() ? Oportunidad.SI : Oportunidad.NO;
            registro.indicarOportunidad(oportunidad, recibido.motivoSinOportunidad(), recibido.motivoOtro());
            if (recibido.noQuiso()) registro.marcarNoQuiso(recibido.nota());
            if (registro instanceof RegistroPorPasos porPasos) {
                if (oportunidad == Oportunidad.SI && !recibido.noQuiso()
                        && recibido.pasos().size() != porPasos.getPasosTotales())
                    return ResultadoRecepcion.RECHAZADO_VALIDACION;
                if ((oportunidad == Oportunidad.NO || recibido.noQuiso()) && !recibido.pasos().isEmpty())
                    return ResultadoRecepcion.RECHAZADO_VALIDACION;
                if (oportunidad == Oportunidad.SI && !recibido.noQuiso()) {
                    var ordenes = recibido.pasos().stream().map(RegistroRecibido.PasoRecibido::orden).distinct().toList();
                    if (ordenes.size() != porPasos.getPasosTotales()
                            || ordenes.stream().anyMatch(orden -> orden < 1 || orden > porPasos.getPasosTotales()))
                        return ResultadoRecepcion.RECHAZADO_VALIDACION;
                }
                for (var paso : recibido.pasos()) porPasos.marcarPaso(paso.orden(), paso.cumplido());
                if (recibido.secuenciaCorrecta() != null) porPasos.indicarSecuencia(recibido.secuenciaCorrecta());
            }
            registro.confirmar(nivel, recibido.nota(), recibido.finCaptura());
            registros.guardar(registro, recibido.camposModificados());
            return ResultadoRecepcion.ACEPTADO;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResultadoRecepcion.RECHAZADO_VALIDACION;
        }
    }

    /** Recalcula y persiste solo la ventana del dominio; ignora cualquier ventana cliente (RF-12, RF-90, DD-34, D-66). */
    public ResultadoRecepcion declararTurno(TurnoDeclarado recibido) {
        TurnoDeclarado recalculado = recalcular(recibido).orElse(null);
        if (recalculado == null) return ResultadoRecepcion.RECHAZADO_VALIDACION;

        turnos.bloquearDeclaracion(recalculado.getCodigo(), recalculado.getCuenta());
        Optional<TurnoDeclarado> existenteOpt = turnos.buscarPorCodigo(recalculado.getCodigo());
        if (existenteOpt.isEmpty()) {
            if (turnos.existeSolapamiento(recalculado.getCodigo(), recalculado.getCuenta(),
                    recalculado.getInicioVentana(), recalculado.getFinVentana())) {
                return ResultadoRecepcion.RECHAZADO_VALIDACION;
            }
            try {
                turnos.insertar(recalculado);
            } catch (DataAccessException ex) {
                if (esTurnoSolapado(ex)) return ResultadoRecepcion.RECHAZADO_VALIDACION;
                throw ex;
            }
            return ResultadoRecepcion.ACEPTADO;
        }

        TurnoDeclarado existente = existenteOpt.get();
        if (!mismoAutorYDeclaracion(existente, recalculado)) return ResultadoRecepcion.RECHAZADO_VALIDACION;
        List<TipoTurno> previos = existente.getEncadenados();
        List<TipoTurno> solicitados = recalculado.getEncadenados();
        if (solicitados.size() < previos.size() || !solicitados.subList(0, previos.size()).equals(previos)) {
            return ResultadoRecepcion.RECHAZADO_VALIDACION;
        }
        if (solicitados.size() == previos.size()) return ResultadoRecepcion.DUPLICADO;

        try {
            List<TipoTurno> nuevos = solicitados.subList(previos.size(), solicitados.size());
            for (TipoTurno tipo : nuevos) existente.encadenar(tipo);
            if (turnos.existeSolapamiento(existente.getCodigo(), existente.getCuenta(),
                    existente.getInicioVentana(), existente.getFinVentana())) {
                return ResultadoRecepcion.RECHAZADO_VALIDACION;
            }
            try {
                turnos.agregarEncadenados(existente.getCodigo(), previos.size() + 1, nuevos,
                        existente.getFinVentana());
            } catch (DataAccessException ex) {
                if (esTurnoSolapado(ex)) return ResultadoRecepcion.RECHAZADO_VALIDACION;
                throw ex;
            }
            return ResultadoRecepcion.ACEPTADO;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResultadoRecepcion.RECHAZADO_VALIDACION;
        }
    }

    /** Solo el autor del turno recibe la descarga operativa sin datos de identidad (RF-101, RF-100, DD-43). */
    @Transactional(readOnly = true)
    public Optional<DatosTurno> obtenerDatosTurno(UUID codigo, CuentaAcceso solicitante) {
        return turnos.buscarPorCodigo(codigo)
                .filter(turno -> turno.getCuenta().getCodigo().equals(solicitante.getCodigo()))
                .map(turno -> consultaDatos.obtener(turno.getInicioVentana().toLocalDate()));
    }

    private Optional<TurnoDeclarado> recalcular(TurnoDeclarado recibido) {
        if (recibido == null || recibido.getTipo() == TipoTurno.D
                || recibido.getDeclaradoEn().isAfter(LocalDateTime.now(LIMA).plusMinutes(TOLERANCIA_FUTURA_MINUTOS))) {
            return Optional.empty();
        }
        try {
            TurnoDeclarado turno = new TurnoDeclarado(recibido.getCodigo(), recibido.getTipo(),
                    recibido.getCuenta(), recibido.getDeclaradoEn());
            for (TipoTurno tipo : recibido.getEncadenados()) turno.encadenar(tipo);
            return Optional.of(turno);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return Optional.empty();
        }
    }

    private boolean mismoAutorYDeclaracion(TurnoDeclarado existente, TurnoDeclarado recibido) {
        return existente.getCuenta().getCodigo().equals(recibido.getCuenta().getCodigo())
                && existente.getTipo() == recibido.getTipo()
                && igualPrecisionDb(existente.getDeclaradoEn(), recibido.getDeclaradoEn());
    }

    private boolean igualPrecisionDb(LocalDateTime izquierda, LocalDateTime derecha) {
        return izquierda.truncatedTo(ChronoUnit.MICROS).equals(derecha.truncatedTo(ChronoUnit.MICROS));
    }

    private boolean esTurnoSolapado(DataAccessException ex) {
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && "23P01".equals(sql.getSQLState())
                    && sql.getMessage().contains("ex_turno_solapado")) return true;
        }
        return false;
    }
}
