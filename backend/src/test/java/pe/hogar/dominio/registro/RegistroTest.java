package pe.hogar.dominio.registro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.hogar.dominio.catalogo.Actividad;
import pe.hogar.dominio.catalogo.BandaFrecuencia;
import pe.hogar.dominio.catalogo.Modalidad;
import pe.hogar.dominio.catalogo.NivelApoyo;
import pe.hogar.dominio.catalogo.Paso;
import pe.hogar.dominio.catalogo.PlantillaPasos;
import pe.hogar.dominio.comun.Bitacora;
import pe.hogar.dominio.residentes.CuentaAcceso;
import pe.hogar.dominio.residentes.Expediente;
import pe.hogar.dominio.residentes.Rol;

/** Datos sintéticos (D-34). */
class RegistroTest {

    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 3, 2, 8, 0);
    private static final NivelApoyo VERBAL = new NivelApoyo("VERBAL", "Apoyo verbal", 0.67, 2);

    private final FabricaRegistro fabrica = new FabricaRegistro();
    private final CuentaAcceso cuidador = new CuentaAcceso("CUI-01", "Cuidador Uno", "cuidador1", "hash", Rol.CUIDADOR, true);
    private final CuentaAcceso coordinadora = new CuentaAcceso("COO-01", "Coordinadora Uno", "coordinadora", "hash", Rol.COORDINADORA, true);
    private final Expediente expediente = new Expediente(UUID.randomUUID(), "EM");
    private final TurnoDeclarado turno = new TurnoDeclarado(UUID.randomUUID(), TipoTurno.M, cuidador, INICIO);

    private static Actividad actividad(Modalidad modalidad) {
        List<Paso> pasos = List.of(new Paso(1, "Preparar la ropa", false), new Paso(2, "Abrir la ducha", false),
                new Paso(3, "Regular el agua", true), new Paso(4, "Enjabonarse", false),
                new Paso(5, "Enjuagarse", false), new Paso(6, "Secarse", false));
        List<PlantillaPasos> plantillas = modalidad == Modalidad.PASOS
                ? List.of(new PlantillaPasos(1, LocalDate.of(2026, 1, 1), null, pasos)) : List.of();
        return new Actividad("A-02", "Ducharse", "A", modalidad, BandaFrecuencia.DIARIA, 1, true, plantillas);
    }

    private RegistroPorPasos ducha() {
        return (RegistroPorPasos) fabrica.crear(actividad(Modalidad.PASOS), expediente, turno, INICIO);
    }

    @Test
    void laModalidadDeLaActividadDecideLaEspecializacion() {
        assertInstanceOf(RegistroPorPasos.class, fabrica.crear(actividad(Modalidad.PASOS), expediente, turno, INICIO));
        assertInstanceOf(RegistroEvento.class, fabrica.crear(actividad(Modalidad.EVENTO), expediente, turno, INICIO));
        assertThrows(UnsupportedOperationException.class,
                () -> fabrica.crear(actividad(Modalidad.VALORACION), expediente, turno, INICIO));
    }

    @Test
    void laAutonomiaEsLaProporcionDePasosPorElPesoDelNivel() {
        RegistroPorPasos r = ducha();
        for (int orden = 1; orden <= 4; orden++) {
            r.marcarPaso(orden, true);
        }
        r.confirmar(VERBAL, null, INICIO.plusMinutes(1));
        assertTrue(r.esPuntuable());
        assertEquals(4.0 / 6 * 0.67, r.calcularAutonomia(), 1e-12);
    }

    @Test
    void conOportunidadElNivelEsObligatorio() {
        RegistroPorPasos r = ducha();
        assertThrows(IllegalArgumentException.class, () -> r.confirmar(null, null, INICIO.plusMinutes(1)));
    }

    @Test
    void sinOportunidadNoHayNivelYNoPuntua() {
        RegistroPorPasos r = ducha();
        r.indicarOportunidad(Oportunidad.NO, MotivoSinOportunidad.FALTAN_RECURSOS, null);
        assertThrows(IllegalArgumentException.class, () -> r.confirmar(VERBAL, null, INICIO.plusMinutes(1)));
        r.confirmar(null, null, INICIO.plusMinutes(1));
        assertFalse(r.esPuntuable());
        assertNull(r.calcularAutonomia());
        assertEquals(Set.of(CampoPrecarga.OPORTUNIDAD), r.getCamposModificados());
    }

    @Test
    void laPrecargaCopiaLosPasosPeroNoElNivelYMarcaLoQueCambia() {
        RegistroPorPasos anterior = ducha();
        anterior.marcarPaso(1, true);
        anterior.marcarPaso(2, true);
        anterior.indicarSecuencia(true);
        anterior.confirmar(VERBAL, null, INICIO.plusMinutes(1));

        RegistroPorPasos nuevo = ducha();
        nuevo.precargarDesde(anterior);
        assertEquals(2, nuevo.getPasosCumplidos());
        assertNull(nuevo.getNivel());
        assertTrue(nuevo.getCamposModificados().isEmpty());

        nuevo.marcarPaso(1, true);
        assertTrue(nuevo.getCamposModificados().isEmpty());
        nuevo.marcarPaso(3, true);
        assertEquals(Set.of(CampoPrecarga.PASOS), nuevo.getCamposModificados());
    }

    @Test
    void anularExigeMotivoYConservaElRegistroFueraDelCalculo() {
        RegistroPorPasos r = ducha();
        r.marcarPaso(1, true);
        r.confirmar(VERBAL, null, INICIO.plusMinutes(1));
        assertThrows(IllegalArgumentException.class, () -> r.anular(" ", coordinadora));

        Bitacora asiento = r.anular("Se registró en el residente equivocado", coordinadora);
        assertEquals(EstadoRegistro.ANULADO, r.getEstado());
        assertFalse(r.esPuntuable());
        assertEquals(r.getCodigo(), asiento.getIdentificadorEntidad());
        assertEquals("CONFIRMADO", asiento.getValorAnterior());
        assertEquals(coordinadora, asiento.getAutor());
    }

    @Test
    void unRegistroConfirmadoYaNoSeModifica() {
        RegistroPorPasos r = ducha();
        r.confirmar(VERBAL, null, INICIO.plusMinutes(1));
        assertThrows(IllegalStateException.class, () -> r.marcarPaso(1, true));
    }
}
