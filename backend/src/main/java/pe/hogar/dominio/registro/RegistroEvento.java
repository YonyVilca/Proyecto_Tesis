package pe.hogar.dominio.registro;

import java.time.LocalDateTime;
import pe.hogar.dominio.catalogo.Actividad;
import pe.hogar.dominio.residentes.Expediente;

/** Registro de un evento [A1]: participación y nivel de apoyo, sin pasos (DC-03). */
public final class RegistroEvento extends Registro {

    private boolean participo = true;

    RegistroEvento(Actividad actividad, Expediente expediente, TurnoDeclarado turno,
                   LocalDateTime inicioCaptura, OrigenRegistro origen) {
        super(actividad, expediente, turno, inicioCaptura, origen);
    }

    public void indicarParticipacion(boolean participo) {
        exigirEstado(EstadoRegistro.A_MEDIAS);
        this.participo = participo;
    }

    /** RB-05: en un evento la autonomía es el peso del nivel. */
    @Override
    public Double calcularAutonomia() {
        return esPuntuable() ? getNivel().peso() : null;
    }

    /** RB-09: además de lo común, el residente participó. */
    @Override
    public boolean esPuntuable() {
        return cumpleCondicionesComunes() && participo;
    }

    public boolean isParticipo() { return participo; }
}
