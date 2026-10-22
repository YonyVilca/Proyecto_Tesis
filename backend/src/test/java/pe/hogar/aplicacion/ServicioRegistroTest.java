package pe.hogar.aplicacion;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.hogar.dominio.registro.TipoTurno;
import pe.hogar.dominio.registro.TurnoDeclarado;
import pe.hogar.dominio.registro.OrigenRegistro;
import pe.hogar.dominio.registro.CampoPrecarga;
import pe.hogar.dominio.registro.MotivoSinOportunidad;
import pe.hogar.dominio.catalogo.Actividad;
import pe.hogar.dominio.catalogo.BandaFrecuencia;
import pe.hogar.dominio.catalogo.Modalidad;
import pe.hogar.dominio.catalogo.NivelApoyo;
import pe.hogar.dominio.catalogo.Paso;
import pe.hogar.dominio.catalogo.PlantillaPasos;
import pe.hogar.dominio.residentes.Expediente;
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

    @Test void recepcionIdempotenteYValidacionesDeTurno() {
        FakeTurnos ts = new FakeTurnos(); FakeRegistros rs = new FakeRegistros(); FakeCatalogo cat = new FakeCatalogo();
        ServicioRegistro servicio = servicio(ts, rs, cat);
        LocalDateTime inicio = horaActual(); UUID turnoId = UUID.randomUUID();
        ts.insertar(turno(turnoId, cuidador, tipoPara(inicio), inicio));
        RegistroRecibido correcto = recibido(UUID.randomUUID(), turnoId, inicio, true, "VERBAL", seisPasos());
        assertEquals(ResultadoRecepcion.ACEPTADO, servicio.recibirRegistro(correcto, cuidador));
        assertEquals(ResultadoRecepcion.DUPLICADO, servicio.recibirRegistro(correcto, cuidador));
        assertEquals(1, rs.guardados.size());
        assertEquals(ResultadoRecepcion.RECHAZADO_SIN_TURNO,
                servicio.recibirRegistro(recibido(UUID.randomUUID(), UUID.randomUUID(), inicio, true, "VERBAL", seisPasos()), cuidador));
        ts.insertar(turno(UUID.randomUUID(), cuenta("CUI-02", Rol.CUIDADOR), TipoTurno.M, inicio));
        UUID otro = ts.guardados.keySet().stream().filter(id -> !id.equals(turnoId)).findFirst().orElseThrow();
        assertEquals(ResultadoRecepcion.RECHAZADO_SIN_TURNO,
                servicio.recibirRegistro(recibido(UUID.randomUUID(), otro, inicio, true, "VERBAL", seisPasos()), cuidador));
        assertEquals(ResultadoRecepcion.RECHAZADO_FUERA_DE_VENTANA,
                servicio.recibirRegistro(recibido(UUID.randomUUID(), turnoId, inicio.toLocalDate().atStartOfDay(), true, "VERBAL", seisPasos()), cuidador));
        assertEquals(ResultadoRecepcion.RECHAZADO_VALIDACION,
                servicio.recibirRegistro(recibido(UUID.randomUUID(), turnoId, inicio, true, null, seisPasos()), cuidador));
        assertEquals(ResultadoRecepcion.RECHAZADO_VALIDACION,
                servicio.recibirRegistro(recibido(UUID.randomUUID(), turnoId, inicio, true, "VERBAL", seisPasos().subList(0, 5)), cuidador));
        assertEquals(ResultadoRecepcion.ACEPTADO,
                servicio.recibirRegistro(recibido(UUID.randomUUID(), turnoId, inicio, false, null, List.of()), cuidador));
    }

    private RegistroRecibido recibido(UUID id, UUID turno, LocalDateTime inicio, boolean oportunidad,
            String nivel, List<RegistroRecibido.PasoRecibido> pasos) {
        return new RegistroRecibido(id, turno, UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "A-02", 1, inicio, inicio.plusMinutes(1), OrigenRegistro.TACTIL, oportunidad,
                oportunidad ? null : MotivoSinOportunidad.FALTAN_RECURSOS, null, false, null, nivel, pasos,
                true, Set.<CampoPrecarga>of());
    }
    private List<RegistroRecibido.PasoRecibido> seisPasos() {
        return java.util.stream.IntStream.rangeClosed(1, 6).mapToObj(i -> new RegistroRecibido.PasoRecibido(i, true)).toList();
    }
    private ServicioRegistro servicio(FakeTurnos turnos, FakeRegistros registros, FakeCatalogo catalogo) {
        ConsultaDatosTurno consulta = fecha -> new DatosTurno(List.of(), List.of(), List.of(), null, List.of());
        return new ServicioRegistro(turnos, consulta, registros, catalogo);
    }

    private ServicioRegistro servicio(FakeTurnos turnos) {
        ConsultaDatosTurno consulta = fecha -> new DatosTurno(List.of(), List.of(), List.of(), null, List.of());
        return new ServicioRegistro(turnos, consulta, new FakeRegistros(), new FakeCatalogo());
    }

    private TurnoDeclarado turno(UUID codigo, CuentaAcceso cuenta, TipoTurno tipo, LocalDateTime hora,
                                 TipoTurno... encadenados) {
        TurnoDeclarado turno = new TurnoDeclarado(codigo, tipo, cuenta, hora);
        for (TipoTurno encadenado : encadenados) turno.encadenar(encadenado);
        return turno;
    }

    private LocalDateTime horaActual() { return LocalDateTime.now(LIMA).minusMinutes(5); }

    private TipoTurno tipoPara(LocalDateTime hora) {
        return hora.getHour() < 7 || hora.getHour() >= 19 ? TipoTurno.N
                : hora.getHour() < 13 ? TipoTurno.M : TipoTurno.T;
    }

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

    private static final class FakeRegistros implements RepositorioRegistros {
        private final Map<UUID, pe.hogar.dominio.registro.Registro> guardados = new HashMap<>();
        @Override public boolean existe(UUID codigo) { return guardados.containsKey(codigo); }
        @Override public void guardar(pe.hogar.dominio.registro.Registro registro, Set<CampoPrecarga> campos) { guardados.put(registro.getCodigo(), registro); }
    }
    private static final class FakeCatalogo implements RepositorioCatalogoRegistro {
        private final UUID expId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        private final Actividad actividad = new Actividad("A-02", "Actividad sintética", "A", Modalidad.PASOS,
                BandaFrecuencia.DIARIA, 1, true, List.of(new PlantillaPasos(1, java.time.LocalDate.of(2026, 1, 1), null,
                java.util.stream.IntStream.rangeClosed(1, 6).mapToObj(i -> new Paso(i, "Paso sintético " + i, false)).toList())));
        @Override public Optional<Expediente> buscarExpedienteActivo(UUID codigo) {
            return expId.equals(codigo) ? Optional.of(new Expediente(codigo, "ES")) : Optional.empty();
        }
        @Override public Optional<Actividad> buscarActividad(String codigo) { return "A-02".equals(codigo) ? Optional.of(actividad) : Optional.empty(); }
        @Override public Optional<NivelApoyo> buscarNivel(String codigo) {
            return "VERBAL".equals(codigo) ? Optional.of(new NivelApoyo("VERBAL", "Verbal", .6, 2)) : Optional.empty();
        }
    }
}
