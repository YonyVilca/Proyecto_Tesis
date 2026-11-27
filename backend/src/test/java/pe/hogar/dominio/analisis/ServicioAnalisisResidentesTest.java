package pe.hogar.dominio.analisis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.hogar.dominio.catalogo.Actividad;
import pe.hogar.dominio.catalogo.BandaFrecuencia;
import pe.hogar.dominio.catalogo.Modalidad;
import pe.hogar.dominio.catalogo.NivelApoyo;
import pe.hogar.dominio.catalogo.Paso;
import pe.hogar.dominio.catalogo.PlantillaPasos;
import pe.hogar.dominio.comun.Periodo;
import pe.hogar.dominio.registro.FabricaRegistro;
import pe.hogar.dominio.registro.OrigenRegistro;
import pe.hogar.dominio.registro.Registro;
import pe.hogar.dominio.registro.RegistroEvento;
import pe.hogar.dominio.registro.RegistroPorPasos;
import pe.hogar.dominio.registro.TipoTurno;
import pe.hogar.dominio.registro.TurnoDeclarado;
import pe.hogar.dominio.residentes.ActividadResidente;
import pe.hogar.dominio.residentes.CuentaAcceso;
import pe.hogar.dominio.residentes.EstadoActividadResidente;
import pe.hogar.dominio.residentes.Expediente;
import pe.hogar.dominio.residentes.Rol;

/** Casos de OT-09A para la fecha de corte y el fundamento visible (RF-99, RF-53, RB-08, RB-12, DD-57). */
class ServicioAnalisisResidentesTest {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Actividad ACTIVIDAD = new Actividad("A-SINTETICA", "Actividad sintética", "EJE",
            Modalidad.EVENTO, BandaFrecuencia.ESPORADICA, 1, false, List.of());
    private static final Expediente EXPEDIENTE = new Expediente(UUID.randomUUID(), "ES");
    private static final CuentaAcceso CUENTA = new CuentaAcceso("cuenta-sintetica", "Cuidador Sintético",
            "cuidador-sintetico", "hash-sintetico", Rol.CUIDADOR, true);
    private static final NivelApoyo VERBAL = new NivelApoyo("VERBAL", "Apoyo verbal", 0.7, 2);

    @Test
    void usaLaVigenciaDeHoyAunqueElPlanCambieLuegoEnElMes() {
        LocalDate hoy = LocalDate.now(LIMA);
        LocalDate manana = hoy.plusDays(1);
        Periodo periodo = new Periodo(hoy.getYear(), hoy.getMonthValue());
        List<ActividadResidente> plan = List.of(
                plan(EstadoActividadResidente.PRIORITARIA, periodo.inicio(), hoy),
                plan(EstadoActividadResidente.NO_CORRESPONDE, manana, null));

        List<IndicadorActividad> indicadores = servicio().calcularIndicadores(EXPEDIENTE, periodo, plan, List.of(), hoy);

        assertEquals(1, indicadores.size());
        assertEquals(ACTIVIDAD.getCodigo(), indicadores.getFirst().getActividad().getCodigo());
    }

    @Test
    void noCuentaNegativasPosterioresALaFechaDeCorte() {
        LocalDate hoy = LocalDate.now(LIMA);
        Periodo periodo = new Periodo(hoy.getYear(), hoy.getMonthValue());
        Registro negativaFutura = registroNoQuiso(hoy.plusDays(1));
        ActividadResidente mantenimiento = plan(EstadoActividadResidente.MANTENIMIENTO, periodo.inicio(), null);

        IndicadorActividad indicador = servicio().calcularIndicadores(EXPEDIENTE, periodo,
                List.of(mantenimiento), List.of(negativaFutura), hoy).getFirst();

        assertEquals(0, indicador.getNegativas());
    }

    @Test
    void fundamentoDeSinDatosIncluyeLosRegistrosConsiderados() {
        LocalDate hoy = LocalDate.now(LIMA);
        Periodo periodo = new Periodo(hoy.getYear(), hoy.getMonthValue());
        Registro primero = registroConOportunidad(hoy.minusDays(1));
        Registro segundo = registroConOportunidad(hoy);
        ActividadResidente mantenimiento = plan(EstadoActividadResidente.MANTENIMIENTO, periodo.inicio(), null);

        IndicadorActividad indicador = servicio().calcularIndicadores(EXPEDIENTE, periodo,
                List.of(mantenimiento), List.of(primero, segundo), hoy).getFirst();

        assertFalse(indicador.isDatosSuficientes());
        assertEquals(EstadoVisual.SIN_DATOS, indicador.getEstadoVisual());
        assertEquals("RB-08 datos suficientes", indicador.obtenerFundamento().getRegla());
        assertEquals(0.6, indicador.obtenerFundamento().getUmbral());
        assertTrue(indicador.obtenerFundamento().getTexto().contains("2 registros con oportunidad; se necesitan 5"));
        assertEquals(List.of(primero.getCodigo(), segundo.getCodigo()),
                indicador.obtenerFundamento().obtenerRegistrosOrigen().stream().map(Registro::getCodigo).toList());
    }

    @Test
    void calculaCadaActividadConSusPropiosRegistrosYFundamentos() {
        LocalDate corte = LocalDate.of(2025, 1, 1);
        Periodo periodo = new Periodo(2025, 1);
        Actividad pasos = actividadPasos("A-PASOS");
        Actividad evento = actividadEvento("B-EVENTO");
        List<ActividadResidente> plan = List.of(
                plan(pasos, EstadoActividadResidente.PRIORITARIA, corte, null),
                plan(evento, EstadoActividadResidente.MANTENIMIENTO, corte, null));
        Registro pasoUno = registroPasos(pasos, corte, true);
        Registro pasoDos = registroPasos(pasos, corte, true);
        Registro pasoTres = registroPasos(pasos, corte, true);
        Registro eventoRegistro = registroEvento(evento, corte);

        List<IndicadorActividad> indicadores = servicio().calcularIndicadores(EXPEDIENTE, periodo, plan,
                List.of(pasoUno, eventoRegistro, pasoDos, pasoTres), corte);

        assertEquals(2, indicadores.size());
        IndicadorActividad indicadorPasos = indicadores.get(0);
        IndicadorActividad indicadorEvento = indicadores.get(1);
        assertEquals("A-PASOS", indicadorPasos.getActividad().getCodigo());
        assertEquals(EstadoVisual.DOMINA, indicadorPasos.getEstadoVisual());
        assertEquals(Set.of(pasoUno.getCodigo(), pasoDos.getCodigo(), pasoTres.getCodigo()),
                indicadorPasos.obtenerFundamento().obtenerRegistrosOrigen().stream().map(Registro::getCodigo).collect(java.util.stream.Collectors.toSet()));
        assertEquals("B-EVENTO", indicadorEvento.getActividad().getCodigo());
        assertEquals(EstadoVisual.SIN_DATOS, indicadorEvento.getEstadoVisual());
        assertEquals(List.of(eventoRegistro.getCodigo()), indicadorEvento.obtenerFundamento()
                .obtenerRegistrosOrigen().stream().map(Registro::getCodigo).toList());
    }

    @Test
    void usaElEstadoPrioritariaOMantenimientoDeCadaActividad() {
        LocalDate corte = LocalDate.of(2025, 1, 1);
        Periodo periodo = new Periodo(2025, 1);
        Actividad pasos = actividadPasos("A-PRIORITARIA");
        Actividad evento = actividadEvento("B-MANTENIMIENTO");
        List<ActividadResidente> plan = List.of(
                plan(pasos, EstadoActividadResidente.PRIORITARIA, corte, null),
                plan(evento, EstadoActividadResidente.MANTENIMIENTO, corte, null));
        Registro prioridad = registroPasos(pasos, corte, false);
        Registro mantenimiento = registroEvento(evento, corte);

        List<IndicadorActividad> indicadores = servicio().calcularIndicadores(EXPEDIENTE, periodo, plan,
                List.of(prioridad, mantenimiento), corte);

        assertTrue(indicadores.get(0).isDatosSuficientes());
        assertEquals(EstadoVisual.EN_TRABAJO, indicadores.get(0).getEstadoVisual());
        assertFalse(indicadores.get(1).isDatosSuficientes());
        assertEquals(EstadoVisual.SIN_DATOS, indicadores.get(1).getEstadoVisual());
    }

    @Test
    void actividadPorPasosYEventoDelMismoResidenteNoMezclanTipos() {
        LocalDate corte = LocalDate.of(2025, 1, 1);
        Periodo periodo = new Periodo(2025, 1);
        Actividad pasos = actividadPasos("A-TIPOS");
        Actividad evento = actividadEvento("B-TIPOS");
        Registro registroPasos = registroPasos(pasos, corte, true);
        Registro registroEvento = registroEvento(evento, corte);

        List<IndicadorActividad> indicadores = servicio().calcularIndicadores(EXPEDIENTE, periodo,
                List.of(plan(pasos, EstadoActividadResidente.MANTENIMIENTO, corte, null),
                        plan(evento, EstadoActividadResidente.MANTENIMIENTO, corte, null)),
                List.of(registroEvento, registroPasos), corte);

        assertEquals(2, indicadores.size());
        assertEquals(EstadoVisual.SIN_DATOS, indicadores.get(0).getEstadoVisual());
        assertEquals(EstadoVisual.SIN_DATOS, indicadores.get(1).getEstadoVisual());
    }

    @Test
    void laFechaDeCorteFijaVigenciaYVentanaDeNegativas() {
        LocalDate corte = LocalDate.of(2025, 3, 10);
        Periodo periodo = new Periodo(2025, 3);
        Actividad actividadHoy = actividadEvento("A-HOY");
        Actividad actividadFutura = actividadEvento("B-FUTURA");
        Registro negativaIncluida = registroNoQuiso(actividadHoy, corte.minusDays(1));
        Registro negativaPosterior = registroNoQuiso(actividadHoy, corte.plusDays(1));
        List<ActividadResidente> plan = List.of(
                plan(actividadHoy, EstadoActividadResidente.MANTENIMIENTO, periodo.inicio(), corte),
                plan(actividadFutura, EstadoActividadResidente.MANTENIMIENTO, corte.plusDays(1), null));

        List<IndicadorActividad> indicadores = servicio().calcularIndicadores(EXPEDIENTE, periodo, plan,
                List.of(negativaIncluida, negativaPosterior), corte);

        assertEquals(1, indicadores.size());
        assertEquals("A-HOY", indicadores.getFirst().getActividad().getCodigo());
        assertEquals(1, indicadores.getFirst().getNegativas());
    }

    private static ServicioAnalisis servicio() {
        return new ServicioAnalisis(Map.of(
                "analisis.ventana_n", 10.0,
                "analisis.minimo_registros", 5.0,
                "analisis.criterio_dominio", 3.0,
                "analisis.umbral_cobertura", 0.6,
                "analisis.epsilon_estancamiento", 0.02,
                "analisis.techo_estancamiento", 0.8,
                "analisis.ventana_tendencia", 3.0,
                "analisis.sin_oportunidad_minimo", 3.0,
                "analisis.sin_oportunidad_proporcion", 0.3,
                "analisis.ventana_negativas_dias", 7.0));
    }

    private static ActividadResidente plan(EstadoActividadResidente estado, LocalDate desde, LocalDate hasta) {
        return new ActividadResidente(UUID.randomUUID(), ACTIVIDAD, estado, desde, hasta);
    }

    private static ActividadResidente plan(Actividad actividad, EstadoActividadResidente estado,
                                           LocalDate desde, LocalDate hasta) {
        return new ActividadResidente(UUID.randomUUID(), actividad, estado, desde, hasta);
    }

    private static Actividad actividadPasos(String codigo) {
        var plantilla = new PlantillaPasos(1, LocalDate.of(2025, 1, 1), null,
                List.of(new Paso(1, "Paso sintético", false)));
        return new Actividad(codigo, "Actividad por pasos sintética", "EJE", Modalidad.PASOS,
                BandaFrecuencia.DIARIA, 1, false, List.of(plantilla));
    }

    private static Actividad actividadEvento(String codigo) {
        return new Actividad(codigo, "Actividad de evento sintética", "EJE", Modalidad.EVENTO,
                BandaFrecuencia.ESPORADICA, 1, false, List.of());
    }

    private static Registro registroPasos(Actividad actividad, LocalDate fecha, boolean cumplido) {
        RegistroPorPasos registro = (RegistroPorPasos) crearRegistro(actividad, fecha);
        registro.marcarPaso(1, cumplido);
        registro.confirmar(INDEPENDIENTE, null, fecha.atTime(9, 5));
        return registro;
    }

    private static Registro registroEvento(Actividad actividad, LocalDate fecha) {
        RegistroEvento registro = (RegistroEvento) crearRegistro(actividad, fecha);
        registro.indicarParticipacion(true);
        registro.confirmar(INDEPENDIENTE, null, fecha.atTime(9, 5));
        return registro;
    }

    private static Registro crearRegistro(Actividad actividad, LocalDate fecha) {
        LocalDateTime momento = fecha.atTime(9, 0);
        TurnoDeclarado turno = new TurnoDeclarado(UUID.randomUUID(), TipoTurno.M, CUENTA, momento);
        return new FabricaRegistro().crear(actividad, EXPEDIENTE, turno, momento, OrigenRegistro.TACTIL);
    }

    private static Registro registroNoQuiso(Actividad actividad, LocalDate fecha) {
        Registro registro = crearRegistro(actividad, fecha);
        registro.marcarNoQuiso(null);
        registro.confirmar(null, null, fecha.atTime(9, 5));
        return registro;
    }

    private static final NivelApoyo INDEPENDIENTE = new NivelApoyo("INDEPENDIENTE", "Independiente", 1.0, 1);

    private static Registro registroNoQuiso(LocalDate fecha) {
        Registro registro = crearRegistro(fecha);
        registro.marcarNoQuiso(null);
        registro.confirmar(null, null, fecha.atTime(9, 5));
        return registro;
    }

    private static Registro registroConOportunidad(LocalDate fecha) {
        Registro registro = crearRegistro(fecha);
        registro.confirmar(VERBAL, null, fecha.atTime(9, 5));
        return registro;
    }

    private static Registro crearRegistro(LocalDate fecha) {
        LocalDateTime momento = fecha.atTime(9, 0);
        TurnoDeclarado turno = new TurnoDeclarado(UUID.randomUUID(), TipoTurno.M, CUENTA, momento);
        return new FabricaRegistro().crear(ACTIVIDAD, EXPEDIENTE, turno, momento, OrigenRegistro.TACTIL);
    }
}
