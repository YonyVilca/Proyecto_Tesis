package pe.hogar.dominio.registro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.hogar.dominio.residentes.CuentaAcceso;
import pe.hogar.dominio.residentes.Rol;

class TurnoDeclaradoTest {

    private final CuentaAcceso cuidador = new CuentaAcceso(UUID.randomUUID(), "cuidador1", Rol.CUIDADOR);

    private TurnoDeclarado turno(TipoTurno tipo, LocalDateTime momento) {
        return new TurnoDeclarado(UUID.randomUUID(), tipo, cuidador, momento);
    }

    @Test
    void sugiereElTipoSegunLaHora() {
        assertEquals(TipoTurno.M, TurnoDeclarado.sugerirTipo(LocalDateTime.of(2026, 3, 2, 7, 0)));
        assertEquals(TipoTurno.T, TurnoDeclarado.sugerirTipo(LocalDateTime.of(2026, 3, 2, 13, 0)));
        assertEquals(TipoTurno.N, TurnoDeclarado.sugerirTipo(LocalDateTime.of(2026, 3, 2, 19, 0)));
        assertEquals(TipoTurno.N, TurnoDeclarado.sugerirTipo(LocalDateTime.of(2026, 3, 2, 6, 59)));
    }

    @Test
    void laNocheDeclaradaDeMadrugadaEmpiezaElDiaAnterior() {
        TurnoDeclarado n = turno(TipoTurno.N, LocalDateTime.of(2026, 3, 2, 2, 30));
        assertEquals(LocalDateTime.of(2026, 3, 1, 19, 0), n.getInicioVentana());
        assertEquals(LocalDateTime.of(2026, 3, 2, 7, 0), n.getFinVentana());
    }

    @Test
    void elServidorToleraQuinceMinutosYElTelefonoNinguno() {
        TurnoDeclarado m = turno(TipoTurno.M, LocalDateTime.of(2026, 3, 2, 7, 5));
        LocalDateTime trasElCierre = LocalDateTime.of(2026, 3, 2, 13, 10);
        assertFalse(m.contieneMomento(trasElCierre));
        assertTrue(m.contieneMomento(trasElCierre, 15));
        assertFalse(m.contieneMomento(LocalDateTime.of(2026, 3, 2, 13, 16), 15));
    }

    @Test
    void elDescansoNoHabilitaRegistro() {
        TurnoDeclarado d = turno(TipoTurno.D, LocalDateTime.of(2026, 3, 2, 9, 0));
        assertFalse(d.habilitaRegistro());
        assertFalse(d.contieneMomento(LocalDateTime.of(2026, 3, 2, 9, 0), 15));
    }

    @Test
    void encadenarAmpliaLaVentanaSinCrearOtroTurno() {
        TurnoDeclarado m = turno(TipoTurno.M, LocalDateTime.of(2026, 3, 2, 7, 5));
        m.encadenar(TipoTurno.T);
        assertEquals(LocalDateTime.of(2026, 3, 2, 19, 0), m.getFinVentana());
        assertEquals(java.util.List.of(TipoTurno.T), m.getEncadenados());
        assertTrue(m.contieneMomento(LocalDateTime.of(2026, 3, 2, 18, 0)));
    }

    @Test
    void noSeEncadenaUnTurnoQueNoEsContiguo() {
        TurnoDeclarado m = turno(TipoTurno.M, LocalDateTime.of(2026, 3, 2, 7, 5));
        assertThrows(IllegalArgumentException.class, () -> m.encadenar(TipoTurno.N));
    }
}
