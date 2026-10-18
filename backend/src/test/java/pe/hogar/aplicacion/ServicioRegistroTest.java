package pe.hogar.aplicacion;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.hogar.dominio.registro.TipoTurno;
import pe.hogar.dominio.registro.TurnoDeclarado;
import pe.hogar.dominio.residentes.CuentaAcceso;
import pe.hogar.dominio.residentes.Rol;
import static org.junit.jupiter.api.Assertions.*;

class ServicioRegistroTest {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final CuentaAcceso cuidador = cuenta("CUI-01", Rol.CUIDADOR);

    @Test void turnoNuevoSeAcepta() {
        FakeTurnos turnos = new FakeTurnos();
        ServicioRegistro servicio = servicio(turnos);
        TurnoDeclarado turno = turno(UUID.randomUUID(), cuidador, TipoTurno.M, horaActual());

        assertEquals(ResultadoRecepcion.ACEPTADO, servicio.declararTurno(turno));
        assertTrue(turnos.guardados.containsKey(turno.getCodigo()));
    }

    @Test void repetirMismaDeclaracionDevuelveDuplicado() {
        FakeTurnos turnos = new FakeTurnos();
        ServicioRegistro servicio = servicio(turnos);
        UUID codigo = UUID.randomUUID();
        LocalDateTime hora = horaActual();
        assertEquals(ResultadoRecepcion.ACEPTADO, servicio.declararTurno(turno(codigo, cuidador, TipoTurno.M, hora)));

        assertEquals(ResultadoRecepcion.DUPLICADO, servicio.declararTurno(turno(codigo, cuidador, TipoTurno.M, hora)));
    }

    @Test void encadenarTurnoContiguoAceptaYExtiendeVentana() {
        FakeTurnos turnos = new FakeTurnos();
        ServicioRegistro servicio = servicio(turnos);
        UUID codigo = UUID.randomUUID();
        LocalDateTime hora = horaActual();
        assertEquals(ResultadoRecepcion.ACEPTADO, servicio.declararTurno(turno(codigo, cuidador, TipoTurno.M, hora)));
        TurnoDeclarado encadenado = turno(codigo, cuidador, TipoTurno.M, hora, TipoTurno.T);

        assertEquals(ResultadoRecepcion.ACEPTADO, servicio.declararTurno(encadenado));
        assertEquals(hora.toLocalDate().atTime(19, 0), turnos.guardados.get(codigo).getFinVentana());
    }

    @Test void descansoYDeclaracionFuturaSeRechazan() {
        ServicioRegistro servicio = servicio(new FakeTurnos());
        assertEquals(ResultadoRecepcion.RECHAZADO_VALIDACION,
                servicio.declararTurno(turno(UUID.randomUUID(), cuidador, TipoTurno.D, horaActual())));
        assertEquals(ResultadoRecepcion.RECHAZADO_VALIDACION,
                servicio.declararTurno(turno(UUID.randomUUID(), cuidador, TipoTurno.M, horaActual().plusMinutes(21))));
    }

    @Test void mismoCodigoDeOtroAutorYSolapamientoSeRechazan() {
        FakeTurnos turnos = new FakeTurnos();
        ServicioRegistro servicio = servicio(turnos);
        UUID codigo = UUID.randomUUID();
        LocalDateTime hora = horaActual();
        assertEquals(ResultadoRecepcion.ACEPTADO, servicio.declararTurno(turno(codigo, cuidador, TipoTurno.M, hora)));
        assertEquals(ResultadoRecepcion.RECHAZADO_VALIDACION,
                servicio.declararTurno(turno(codigo, cuenta("CUI-02", Rol.CUIDADOR), TipoTurno.M, hora)));
        turnos.solapado = true;
        assertEquals(ResultadoRecepcion.RECHAZADO_VALIDACION,
                servicio.declararTurno(turno(UUID.randomUUID(), cuidador, TipoTurno.M, hora.plusHours(1))));
    }

    @Test void soloElAutorPuedeObtenerLosDatosDelTurno() {
        FakeTurnos turnos = new FakeTurnos();
        ServicioRegistro servicio = servicio(turnos);
        UUID codigo = UUID.randomUUID();
        TurnoDeclarado turno = turno(codigo, cuidador, TipoTurno.M, horaActual());
        turnos.insertar(turno);

        assertTrue(servicio.obtenerDatosTurno(codigo, cuenta("CUI-02", Rol.CUIDADOR)).isEmpty());
        assertTrue(servicio.obtenerDatosTurno(UUID.randomUUID(), cuidador).isEmpty());
    }

    private ServicioRegistro servicio(FakeTurnos turnos) {
        ConsultaDatosTurno consulta = fecha -> new DatosTurno(List.of(), List.of(), List.of(), null, List.of());
        return new ServicioRegistro(turnos, consulta);
    }

    private TurnoDeclarado turno(UUID codigo, CuentaAcceso cuenta, TipoTurno tipo, LocalDateTime hora,
                                 TipoTurno... encadenados) {
        TurnoDeclarado turno = new TurnoDeclarado(codigo, tipo, cuenta, hora);
        for (TipoTurno encadenado : encadenados) turno.encadenar(encadenado);
        return turno;
    }

    private LocalDateTime horaActual() { return LocalDateTime.now(LIMA).minusMinutes(5); }

    private CuentaAcceso cuenta(String codigo, Rol rol) {
        return new CuentaAcceso(codigo, "Nombre Sintético", codigo.toLowerCase(), "hash", rol, true);
    }

    private static final class FakeTurnos implements RepositorioTurnos {
        private final Map<UUID, TurnoDeclarado> guardados = new HashMap<>();
        private boolean solapado;

        @Override public void bloquearDeclaracion(UUID codigo, CuentaAcceso cuenta) { }
        @Override public Optional<TurnoDeclarado> buscarPorCodigo(UUID codigo) {
            return Optional.ofNullable(guardados.get(codigo)).map(FakeTurnos::copiar);
        }
        @Override public boolean existeSolapamiento(UUID codigo, CuentaAcceso cuenta, LocalDateTime inicio, LocalDateTime fin) {
            return solapado;
        }
        @Override public void insertar(TurnoDeclarado turno) { guardados.put(turno.getCodigo(), copiar(turno)); }
        @Override public void agregarEncadenados(UUID codigo, int siguienteOrden, List<TipoTurno> nuevos,
                                                 LocalDateTime finVentana) {
            TurnoDeclarado turno = copiar(guardados.get(codigo));
            nuevos.forEach(turno::encadenar);
            guardados.put(codigo, turno);
        }
        private static TurnoDeclarado copiar(TurnoDeclarado original) {
            TurnoDeclarado copia = new TurnoDeclarado(original.getCodigo(), original.getTipo(), original.getCuenta(),
                    original.getDeclaradoEn());
            original.getEncadenados().forEach(copia::encadenar);
            return copia;
        }
    }
}
