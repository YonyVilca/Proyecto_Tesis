package pe.hogar.dominio.analisis;

import java.util.List;
import java.util.Objects;
import pe.hogar.dominio.registro.Registro;

/** Por qué una actividad muestra su estado: regla, umbral, texto y registros de origen (RF-53). */
public final class Fundamento {

    private final String regla;
    private final Double umbral;
    private final String texto;
    private final List<Registro> registrosOrigen;

    public Fundamento(String regla, Double umbral, String texto, List<Registro> registrosOrigen) {
        this.regla = Objects.requireNonNull(regla);
        this.umbral = umbral;
        this.texto = Objects.requireNonNull(texto);
        this.registrosOrigen = List.copyOf(registrosOrigen);
    }

    public List<Registro> obtenerRegistrosOrigen() {
        return registrosOrigen;
    }

    public String getRegla() { return regla; }
    public Double getUmbral() { return umbral; }
    public String getTexto() { return texto; }
}
