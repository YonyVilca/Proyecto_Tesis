package pe.hogar.dominio.analisis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.hogar.dominio.catalogo.Actividad;
import pe.hogar.dominio.catalogo.BandaFrecuencia;
import pe.hogar.dominio.catalogo.Modalidad;
import pe.hogar.dominio.catalogo.NivelApoyo;
import pe.hogar.dominio.comun.Periodo;
import pe.hogar.dominio.registro.FabricaRegistro;
import pe.hogar.dominio.registro.OrigenRegistro;
import pe.hogar.dominio.registro.Registro;
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

        List<IndicadorActividad> indicadores = servicio().calcularIndicadores(EXPEDIENTE, periodo, plan, List.of());

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
                List.of(mantenimiento), List.of(negativaFutura)).getFirst();

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
                List.of(mantenimiento), List.of(primero, segundo)).getFirst();

        assertFalse(indicador.isDatosSuficientes());
        assertEquals(EstadoVisual.SIN_DATOS, indicador.getEstadoVisual());
        assertEquals("RB-08 datos suficientes", indicador.obtenerFundamento().getRegla());
        assertEquals(0.6, indicador.obtenerFundamento().getUmbral());
        assertTrue(indicador.obtenerFundamento().getTexto().contains("2 registros con oportunidad; se necesitan 5"));
        assertEquals(List.of(primero.getCodigo(), segundo.getCodigo()),
                indicador.obtenerFundamento().obtenerRegistrosOrigen().stream().map(Registro::getCodigo).toList());
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
