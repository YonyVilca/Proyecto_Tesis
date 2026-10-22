package pe.hogar.aplicacion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import pe.hogar.dominio.registro.CampoPrecarga;
import pe.hogar.dominio.registro.MotivoSinOportunidad;
import pe.hogar.dominio.registro.OrigenRegistro;

/** DTO de recepción de captura sin evidencia (PD-06, RF-13, RF-17–RF-21, RF-32, RF-65, D-59, D-66). */
public record RegistroRecibido(UUID codigo, UUID turno, UUID expediente, String actividad,
        int versionPlantilla, LocalDateTime inicioCaptura, LocalDateTime finCaptura, OrigenRegistro origen,
        boolean oportunidad, MotivoSinOportunidad motivoSinOportunidad, String motivoOtro, boolean noQuiso,
        String nota, String nivel, List<PasoRecibido> pasos, Boolean secuenciaCorrecta,
        Set<CampoPrecarga> camposModificados) {
    public RegistroRecibido {
        pasos = pasos == null ? List.of() : List.copyOf(pasos);
        camposModificados = camposModificados == null ? Set.of() : Set.copyOf(camposModificados);
    }
    public record PasoRecibido(int orden, boolean cumplido) { }
}
