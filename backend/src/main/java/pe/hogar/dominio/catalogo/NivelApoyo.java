package pe.hogar.dominio.catalogo;

import java.util.Objects;

/** Nivel de la escala de apoyo; su peso entra en la autonomía (RB-05, D-31). */
public record NivelApoyo(String codigo, String nombre, double peso, int orden) {

    public NivelApoyo {
        Objects.requireNonNull(codigo);
        Objects.requireNonNull(nombre);
        if (peso < 0 || peso > 1) {
            throw new IllegalArgumentException("El peso va de 0 a 1");
        }
    }
}
