package pe.hogar.aplicacion.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.hogar.aplicacion.DatosTurno;
import pe.hogar.aplicacion.IRegistro;
import pe.hogar.aplicacion.ResultadoRecepcion;
import pe.hogar.aplicacion.Sesion;
import pe.hogar.dominio.registro.TipoTurno;
import pe.hogar.dominio.registro.TurnoDeclarado;

/** Declaración de turno y descarga operativa (RF-12, RF-90, RF-101, RF-100, D-66, DD-43). */
@RestController
@RequestMapping("/api/turnos")
public class TurnosController {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final IRegistro registro;

    public TurnosController(IRegistro registro) { this.registro = registro; }

    @PutMapping("/{codigo}")
    @PreAuthorize("hasRole('CUIDADOR')")
    public ResponseEntity<ResultadoRespuesta> declarar(@PathVariable UUID codigo,
                                                       @RequestBody SolicitudTurno solicitud,
                                                       Authentication authentication) {
        if (solicitud.tipo() == null || solicitud.declaradoEn() == null) {
            return ResponseEntity.ok(new ResultadoRespuesta(ResultadoRecepcion.RECHAZADO_VALIDACION));
        }
        Sesion sesion = (Sesion) authentication.getPrincipal();
        TurnoDeclarado recibido = new TurnoDeclarado(codigo, solicitud.tipo(), sesion.cuenta(),
                solicitud.declaradoEn().atZoneSameInstant(LIMA).toLocalDateTime());
        try {
            for (TipoTurno encadenado : solicitud.encadenados()) recibido.encadenar(encadenado);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.ok(new ResultadoRespuesta(ResultadoRecepcion.RECHAZADO_VALIDACION));
        }
        return ResponseEntity.ok(new ResultadoRespuesta(registro.declararTurno(recibido)));
    }

    @GetMapping("/{codigo}/datos")
    @PreAuthorize("hasRole('CUIDADOR')")
    public ResponseEntity<DatosTurno> obtenerDatos(@PathVariable UUID codigo, Authentication authentication) {
        Sesion sesion = (Sesion) authentication.getPrincipal();
        return registro.obtenerDatosTurno(codigo, sesion.cuenta()).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record SolicitudTurno(TipoTurno tipo, OffsetDateTime declaradoEn, List<TipoTurno> encadenados) {
        public SolicitudTurno { encadenados = encadenados == null ? List.of() : List.copyOf(encadenados); }
    }
    public record ResultadoRespuesta(ResultadoRecepcion resultado) { }
}
