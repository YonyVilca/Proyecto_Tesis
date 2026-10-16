package pe.hogar.dominio.catalogo;

/** Paso de una plantilla. Un paso crítico impide sugerir el retiro de la supervisión (RF-77). */
public record Paso(int orden, String descripcion, boolean critico) {

    public Paso {
        if (orden < 1) {
            throw new IllegalArgumentException("El orden empieza en 1");
        }
    }
}
