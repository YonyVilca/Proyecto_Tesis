package pe.hogar.aplicacion.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.hogar.aplicacion.IRegistro;
import pe.hogar.aplicacion.RegistroRecibido;
import pe.hogar.aplicacion.ResultadoRecepcion;
import pe.hogar.aplicacion.Sesion;
import pe.hogar.dominio.registro.CampoPrecarga;
import pe.hogar.dominio.registro.MotivoSinOportunidad;
import pe.hogar.dominio.registro.OrigenRegistro;

/** Endpoint de recepción de capturas del teléfono (RF-13, RF-32, D-66). */
@RestController
@RequestMapping("/api/registros")
public class RegistrosController {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final IRegistro registro;
    public RegistrosController(IRegistro registro) { this.registro = registro; }

    @PutMapping("/{codigo}")
    @PreAuthorize("hasRole('CUIDADOR')")
    public ResponseEntity<ResultadoRespuesta> recibir(@PathVariable UUID codigo,
            @RequestBody SolicitudRegistro solicitud, Authentication authentication) {
        Sesion sesion = (Sesion) authentication.getPrincipal();
        RegistroRecibido recibido = new RegistroRecibido(codigo, solicitud.turno(), solicitud.expediente(),
                solicitud.actividad(), solicitud.versionPlantilla(), lima(solicitud.inicioCaptura()),
                lima(solicitud.finCaptura()), solicitud.origen(), solicitud.oportunidad(),
                solicitud.motivoSinOportunidad(), solicitud.motivoOtro(), solicitud.noQuiso(), solicitud.nota(),
                solicitud.nivel(), solicitud.pasos(), solicitud.secuenciaCorrecta(), solicitud.camposModificados());
        return ResponseEntity.ok(new ResultadoRespuesta(registro.recibirRegistro(recibido, sesion.cuenta())));
    }

    private java.time.LocalDateTime lima(OffsetDateTime fecha) {
        return fecha == null ? null : fecha.atZoneSameInstant(LIMA).toLocalDateTime();
    }

    public record SolicitudRegistro(UUID turno, UUID expediente, String actividad, int versionPlantilla,
            OffsetDateTime inicioCaptura, OffsetDateTime finCaptura, OrigenRegistro origen, boolean oportunidad,
            MotivoSinOportunidad motivoSinOportunidad, String motivoOtro, boolean noQuiso, String nota,
            String nivel, List<RegistroRecibido.PasoRecibido> pasos, Boolean secuenciaCorrecta,
            Set<CampoPrecarga> camposModificados) { }
    public record ResultadoRespuesta(ResultadoRecepcion resultado) { }
}
