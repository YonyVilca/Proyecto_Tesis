package pe.hogar.dominio.catalogo;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Versión de los pasos de una actividad. Cambiar los pasos crea otra versión (EM-06). */
public final class PlantillaPasos {

    private final int version;
    private final LocalDate vigenciaDesde;
    private final LocalDate vigenciaHasta;
    private final List<Paso> pasos;

    public PlantillaPasos(int version, LocalDate vigenciaDesde, LocalDate vigenciaHasta, List<Paso> pasos) {
        if (pasos.isEmpty()) {
            throw new IllegalArgumentException("Una plantilla tiene al menos un paso");
        }
        for (int i = 0; i < pasos.size(); i++) {
            if (pasos.get(i).orden() != i + 1) {
                throw new IllegalArgumentException("Los pasos se numeran 1, 2, 3... sin huecos");
            }
        }
        this.version = version;
        this.vigenciaDesde = Objects.requireNonNull(vigenciaDesde);
        this.vigenciaHasta = vigenciaHasta;
        this.pasos = List.copyOf(pasos);
    }

    public List<Paso> obtenerPasos() {
        return pasos;
    }

    public int getTotalPasos() {
        return pasos.size();
    }

    public boolean estaVigente() {
        return vigenciaHasta == null;
    }

    public int getVersion() { return version; }
    public LocalDate getVigenciaDesde() { return vigenciaDesde; }
    public LocalDate getVigenciaHasta() { return vigenciaHasta; }
}
