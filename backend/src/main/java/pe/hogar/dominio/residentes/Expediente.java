package pe.hogar.dominio.residentes;

import java.util.Objects;
import java.util.UUID;

/** Expediente de un residente. Sin datos de identidad, que viven en su propio esquema (RNF-25). */
public final class Expediente {

    private final UUID codigo;
    private final String nombreVisible;
    private final String iniciales;
    private final String color;

    public Expediente(UUID codigo, String iniciales) {
        this(codigo, "", iniciales, "");
    }

    /** Datos mínimos visibles en las tarjetas; excluye identidad, diagnóstico y contactos (RF-100, RNF-25). */
    public Expediente(UUID codigo, String nombreVisible, String iniciales, String color) {
        this.codigo = Objects.requireNonNull(codigo);
        this.nombreVisible = Objects.requireNonNull(nombreVisible);
        this.iniciales = Objects.requireNonNull(iniciales);
        this.color = Objects.requireNonNull(color);
    }

    public UUID getCodigo() { return codigo; }
    public String getNombreVisible() { return nombreVisible; }
    public String getIniciales() { return iniciales; }
    public String getColor() { return color; }
}
