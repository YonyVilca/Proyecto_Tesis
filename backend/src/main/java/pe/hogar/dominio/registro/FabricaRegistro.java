package pe.hogar.dominio.registro;

import java.time.LocalDateTime;
import pe.hogar.dominio.catalogo.Actividad;
import pe.hogar.dominio.residentes.Expediente;

/**
 * Método fábrica parametrizado (PD-12). La especialización la decide la modalidad de la actividad, no
 * quien llama (D-25). Los constructores de Registro son de paquete: nadie más puede crearlos.
 */
public final class FabricaRegistro {

    public Registro crear(Actividad actividad, Expediente expediente, TurnoDeclarado turno,
                          LocalDateTime inicioCaptura) {
        return crear(actividad, expediente, turno, inicioCaptura, OrigenRegistro.TACTIL);
    }

    public Registro crear(Actividad actividad, Expediente expediente, TurnoDeclarado turno,
                          LocalDateTime inicioCaptura, OrigenRegistro origen) {
        return switch (actividad.getModalidad()) {
            case PASOS -> new RegistroPorPasos(actividad, expediente, turno, inicioCaptura, origen,
                    actividad.obtenerPlantillaVigente().orElseThrow(
                            () -> new IllegalStateException("La actividad no tiene plantilla vigente")));
            case EVENTO -> new RegistroEvento(actividad, expediente, turno, inicioCaptura, origen);
            case VALORACION -> throw new UnsupportedOperationException(
                    "La valoración se registra en la tesis final");
        };
    }
}
