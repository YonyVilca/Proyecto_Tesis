package pe.hogar.dominio.registro;

import java.time.LocalDateTime;
import java.util.UUID;
import pe.hogar.dominio.catalogo.Actividad;
import pe.hogar.dominio.catalogo.Modalidad;
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
            case PASOS -> new RegistroPorPasos(UUID.randomUUID(), actividad, expediente, turno, inicioCaptura, origen,
                    actividad.obtenerPlantillaVigente().orElseThrow(
                            () -> new IllegalStateException("La actividad no tiene plantilla vigente")));
            case EVENTO -> new RegistroEvento(UUID.randomUUID(), actividad, expediente, turno, inicioCaptura, origen);
            case VALORACION -> throw new UnsupportedOperationException(
                    "La valoración se registra en la tesis final");
        };
    }

    /** Reconstruye una captura con la plantilla descargada por el teléfono (RF-65, DD-21, PD-12). */
    public Registro reconstruir(UUID codigo, Actividad actividad, Expediente expediente, TurnoDeclarado turno,
                                LocalDateTime inicioCaptura, OrigenRegistro origen, int versionPlantilla) {
        if (actividad.getModalidad() != Modalidad.PASOS) {
            throw new IllegalArgumentException("La reconstrucción con versión requiere modalidad PASOS");
        }
        var plantilla = actividad.obtenerPlantilla(versionPlantilla)
                .orElseThrow(() -> new IllegalArgumentException("No existe la versión de plantilla " + versionPlantilla));
        return new RegistroPorPasos(codigo, actividad, expediente, turno, inicioCaptura, origen, plantilla);
    }
}
