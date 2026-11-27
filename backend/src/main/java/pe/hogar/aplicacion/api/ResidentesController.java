package pe.hogar.aplicacion.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.hogar.aplicacion.IAnalisisAvance;
import pe.hogar.aplicacion.IResidentes;
import pe.hogar.dominio.analisis.Fundamento;
import pe.hogar.dominio.analisis.IndicadorActividad;
import pe.hogar.dominio.catalogo.Actividad;
import pe.hogar.dominio.comun.Periodo;
import pe.hogar.dominio.registro.Registro;
import pe.hogar.dominio.registro.RegistroEvento;
import pe.hogar.dominio.registro.RegistroPorPasos;
import pe.hogar.dominio.residentes.Expediente;

/** Consulta las tarjetas y fundamentos sin exponer atributos del esquema identidad (RF-53, RF-99, RF-100). */
@RestController
@RequestMapping("/api/residentes")
public class ResidentesController {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final IResidentes residentes;
    private final IAnalisisAvance analisis;

    public ResidentesController(IResidentes residentes, IAnalisisAvance analisis) {
        this.residentes = residentes;
        this.analisis = analisis;
    }

    /** Ambos roles con sesión consultan tarjetas en orden de nombre, nunca de rendimiento. */
    @GetMapping
    @PreAuthorize("hasAnyRole('CUIDADOR','COORDINADORA')")
    public List<TarjetaResidente> obtenerTarjetas() {
        LocalDate hoy = LocalDate.now(LIMA);
        Periodo periodo = new Periodo(hoy.getYear(), hoy.getMonthValue());
        return residentes.obtenerExpedientes(hoy).stream()
                .map(expediente -> new TarjetaResidente(expediente.getCodigo(), expediente.getNombreVisible(),
                        expediente.getIniciales(), expediente.getColor(),
                        analisis.calcularIndicadores(expediente, periodo).stream()
                                .map(ResidentesController::actividadRespuesta).toList()))
                .toList();
    }

    /** RF-53: al seleccionar una actividad devuelve regla, umbral, texto y sus registros de origen. */
    @GetMapping("/{codigo}/actividades/{actividad}/fundamento")
    @PreAuthorize("hasAnyRole('CUIDADOR','COORDINADORA')")
    public ResponseEntity<FundamentoRespuesta> obtenerFundamento(@PathVariable UUID codigo,
            @PathVariable String actividad) {
        LocalDate hoy = LocalDate.now(LIMA);
        Expediente expediente = residentes.obtenerExpedientes(hoy).stream()
                .filter(item -> item.getCodigo().equals(codigo)).findFirst().orElse(null);
        if (expediente == null) return ResponseEntity.notFound().build();
        Periodo periodo = new Periodo(hoy.getYear(), hoy.getMonthValue());
        IndicadorActividad indicador = analisis.calcularIndicadores(expediente, periodo).stream()
                .filter(item -> item.getActividad().getCodigo().equals(actividad)).findFirst().orElse(null);
        if (indicador == null || indicador.obtenerFundamento() == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(fundamentoRespuesta(indicador.getActividad(), indicador.obtenerFundamento()));
    }

    private static ActividadRespuesta actividadRespuesta(IndicadorActividad indicador) {
        Actividad actividad = indicador.getActividad();
        return new ActividadRespuesta(actividad.getCodigo(), actividad.getNombre(), indicador.getEstadoVisual(),
                indicador.getNegativas());
    }

    private static FundamentoRespuesta fundamentoRespuesta(Actividad actividad, Fundamento fundamento) {
        return new FundamentoRespuesta(actividad.getCodigo(), actividad.getNombre(), fundamento.getRegla(),
                fundamento.getUmbral(), fundamento.getTexto(), fundamento.obtenerRegistrosOrigen().stream()
                        .map(ResidentesController::registroOrigen).toList());
    }

    private static RegistroOrigenRespuesta registroOrigen(Registro registro) {
        List<PasoOrigen> pasos = registro instanceof RegistroPorPasos porPasos
                ? porPasos.getPasos().stream().map(paso -> new PasoOrigen(paso.getOrden(), paso.isCumplido())).toList()
                : List.of();
        Boolean participo = registro instanceof RegistroEvento evento ? evento.isParticipo() : null;
        return new RegistroOrigenRespuesta(registro.getCodigo(), registro.getFechaHora(),
                registro.getOportunidad().name(), registro.getMotivoSinOportunidad() == null
                        ? null : registro.getMotivoSinOportunidad().name(), registro.isNoQuiso(),
                registro.getNivel() == null ? null : registro.getNivel().nombre(), participo, pasos);
    }

    public record TarjetaResidente(UUID codigo, String nombreVisible, String iniciales, String color,
                                   List<ActividadRespuesta> actividades) {
        public TarjetaResidente { actividades = List.copyOf(actividades); }
    }

    public record ActividadRespuesta(String codigo, String nombre, pe.hogar.dominio.analisis.EstadoVisual estado,
                                     int negativas) { }

    public record FundamentoRespuesta(String actividad, String nombreActividad, String regla, Double umbral,
                                      String texto, List<RegistroOrigenRespuesta> registrosOrigen) {
        public FundamentoRespuesta { registrosOrigen = List.copyOf(registrosOrigen); }
    }

    public record RegistroOrigenRespuesta(UUID codigo, LocalDateTime fechaHora, String oportunidad,
            String motivoSinOportunidad, boolean noQuiso, String nivelApoyo, Boolean participo,
            List<PasoOrigen> pasos) {
        public RegistroOrigenRespuesta { pasos = List.copyOf(pasos); }
    }

    public record PasoOrigen(int orden, boolean cumplido) { }
}
