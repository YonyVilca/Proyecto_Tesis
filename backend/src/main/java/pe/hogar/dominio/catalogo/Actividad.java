package pe.hogar.dominio.catalogo;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Actividad del catálogo. Su modalidad decide qué especialización de Registro se crea (D-25, PD-12). */
public final class Actividad {

    private final String codigo;
    private final String nombre;
    private final String eje;
    private final Modalidad modalidad;
    private final BandaFrecuencia banda;
    private final int frecuenciaEsperada;
    private final boolean secuenciaRelevante;
    private final List<PlantillaPasos> plantillas;

    public Actividad(String codigo, String nombre, String eje, Modalidad modalidad, BandaFrecuencia banda,
                     int frecuenciaEsperada, boolean secuenciaRelevante, List<PlantillaPasos> plantillas) {
        if (frecuenciaEsperada < 1) {
            throw new IllegalArgumentException("La frecuencia esperada es al menos 1");
        }
        if (modalidad == Modalidad.PASOS && plantillas.stream().noneMatch(PlantillaPasos::estaVigente)) {
            throw new IllegalArgumentException("Una actividad por pasos necesita una plantilla vigente");
        }
        this.codigo = Objects.requireNonNull(codigo);
        this.nombre = Objects.requireNonNull(nombre);
        this.eje = Objects.requireNonNull(eje);
        this.modalidad = Objects.requireNonNull(modalidad);
        this.banda = Objects.requireNonNull(banda);
        this.frecuenciaEsperada = frecuenciaEsperada;
        this.secuenciaRelevante = secuenciaRelevante;
        this.plantillas = List.copyOf(plantillas);
    }

    public Optional<PlantillaPasos> obtenerPlantillaVigente() {
        return plantillas.stream().filter(PlantillaPasos::estaVigente).findFirst();
    }

    public Optional<PlantillaPasos> obtenerPlantilla(int version) {
        return plantillas.stream().filter(p -> p.getVersion() == version).findFirst();
    }

    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public String getEje() { return eje; }
    public Modalidad getModalidad() { return modalidad; }
    public BandaFrecuencia getBanda() { return banda; }
    public int getFrecuenciaEsperada() { return frecuenciaEsperada; }
    public boolean isSecuenciaRelevante() { return secuenciaRelevante; }
}
