package pe.hogar.aplicacion;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** DTO de descarga del turno sin datos de identidad (RF-101, RF-100, DD-43, PD-06). */
public record DatosTurno(List<ExpedienteDatos> expedientes,
                         List<ActividadResidenteDatos> actividadesResidente,
                         List<ActividadDatos> actividades,
                         EscalaDatos escala,
                         List<RegistroResumen> ultimosRegistros) {
    public DatosTurno {
        expedientes = List.copyOf(expedientes);
        actividadesResidente = List.copyOf(actividadesResidente);
        actividades = List.copyOf(actividades);
        ultimosRegistros = List.copyOf(ultimosRegistros);
    }

    public record ExpedienteDatos(UUID codigo, String nombreVisible, String iniciales, String color) { }
    public record ActividadResidenteDatos(UUID expediente, String actividad, String estado) { }
    public record ActividadDatos(String codigo, String nombre, String modalidad, String banda,
                                 boolean secuenciaRelevante, PlantillaDatos plantilla) { }
    public record PlantillaDatos(int version, List<PasoDatos> pasos) {
        public PlantillaDatos { pasos = List.copyOf(pasos); }
    }
    public record PasoDatos(int orden, String descripcion, boolean critico) { }
    public record EscalaDatos(int version, String nombre, List<NivelDatos> niveles) {
        public EscalaDatos { niveles = List.copyOf(niveles); }
    }
    public record NivelDatos(String codigo, String nombre, String descripcionObservable,
                             BigDecimal peso, int orden) { }
    /** Precarga del último registro puntuable; nunca contiene el nivel de apoyo (D-41). */
    public record RegistroResumen(UUID codigo, UUID expediente, String actividad, int versionPlantilla,
                                  List<PasoResumen> pasos, Boolean secuenciaCorrecta) {
        public RegistroResumen { pasos = List.copyOf(pasos); }
    }
    public record PasoResumen(int orden, boolean cumplido) { }
}
