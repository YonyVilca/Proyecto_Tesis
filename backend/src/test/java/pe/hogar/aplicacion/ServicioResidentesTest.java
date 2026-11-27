package pe.hogar.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.hogar.dominio.analisis.EstadoVisual;
import pe.hogar.dominio.catalogo.Actividad;
import pe.hogar.dominio.catalogo.BandaFrecuencia;
import pe.hogar.dominio.catalogo.Modalidad;
import pe.hogar.dominio.comun.Periodo;
import pe.hogar.dominio.residentes.ActividadResidente;
import pe.hogar.dominio.residentes.EstadoActividadResidente;
import pe.hogar.dominio.residentes.Expediente;

/** Verifica que ServicioResidentes coordina puertos de lectura y mantiene el dominio aislado (DD-57, SD-07). */
class ServicioResidentesTest {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Test
    void cargaDatosConfirmadosYParametrosParaDelegarElCalculo() {
        LocalDate hoy = LocalDate.now(LIMA);
        Periodo periodo = new Periodo(hoy.getYear(), hoy.getMonthValue());
        Expediente expediente = new Expediente(UUID.randomUUID(), "Residente Sintético", "RS", "#123456");
        Actividad actividad = new Actividad("A-SINTETICA", "Actividad sintética", "EJE", Modalidad.EVENTO,
                BandaFrecuencia.ESPORADICA, 1, false, List.of());
        ActividadResidente plan = new ActividadResidente(UUID.randomUUID(), actividad,
                EstadoActividadResidente.MANTENIMIENTO, periodo.inicio(), null);
        ServicioResidentes servicio = new ServicioResidentes(fecha -> List.of(expediente),
                (codigo, desde, hasta) -> List.of(plan), (codigo, hasta) -> List.of(),
                ServicioResidentesTest::parametros);

        assertEquals(List.of(expediente), servicio.obtenerExpedientes(hoy));
        var indicadores = servicio.calcularIndicadores(expediente, periodo);

        assertEquals(1, indicadores.size());
        assertEquals(EstadoVisual.SIN_DATOS, indicadores.getFirst().getEstadoVisual());
        assertNotNull(indicadores.getFirst().obtenerFundamento());
    }

    private static Map<String, Double> parametros() {
        return Map.of("analisis.ventana_n", 10.0, "analisis.minimo_registros", 5.0,
                "analisis.criterio_dominio", 3.0, "analisis.umbral_cobertura", 0.6,
                "analisis.sin_oportunidad_minimo", 3.0, "analisis.sin_oportunidad_proporcion", 0.3,
                "analisis.ventana_negativas_dias", 7.0);
    }
}
