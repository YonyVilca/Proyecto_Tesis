package pe.hogar.dominio.residentes;

import java.util.Objects;
import java.util.UUID;

/** Expediente de un residente. Sin datos de identidad, que viven en su propio esquema (RNF-25). */
public final class Expediente {

    private final UUID codigo;
    private final String iniciales;

    public Expediente(UUID codigo, String iniciales) {
        this.codigo = Objects.requireNonNull(codigo);
        this.iniciales = Objects.requireNonNull(iniciales);
    }

    public UUID getCodigo() { return codigo; }
    public String getIniciales() { return iniciales; }
}
