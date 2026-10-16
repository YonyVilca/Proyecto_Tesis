package pe.hogar.dominio.analisis;

import java.util.List;

/** Estrategia de tendencia [A6] (PD-11). Sin ella, el núcleo nunca muestra ESTANCADA. */
public interface IEstimadorTendencia {

    /** Pendiente de la autonomía por registro; la serie está ordenada en el tiempo. */
    double estimar(List<Double> serie);
}
