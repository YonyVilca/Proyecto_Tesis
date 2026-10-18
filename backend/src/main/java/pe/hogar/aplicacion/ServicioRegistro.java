package pe.hogar.aplicacion;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.hogar.dominio.registro.TipoTurno;
import pe.hogar.dominio.registro.TurnoDeclarado;
import pe.hogar.dominio.residentes.CuentaAcceso;

/** Recepción idempotente de turnos y lectura de sus datos de captura (RF-12, RF-90, RF-101, RB-11, D-66, DD-34). */
@Service
@Transactional
public class ServicioRegistro implements IRegistro {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final int TOLERANCIA_FUTURA_MINUTOS = 15;

    private final RepositorioTurnos turnos;
    private final ConsultaDatosTurno consultaDatos;

    public ServicioRegistro(RepositorioTurnos turnos, ConsultaDatosTurno consultaDatos) {
        this.turnos = turnos;
        this.consultaDatos = consultaDatos;
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
            turnos.insertar(recalculado);
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
            turnos.agregarEncadenados(existente.getCodigo(), previos.size() + 1, nuevos, existente.getFinVentana());
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
}
