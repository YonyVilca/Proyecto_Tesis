package pe.hogar.dominio.analisis;

import java.util.List;

/** Pendiente por mínimos cuadrados sobre los últimos registros de la serie (RF-51, EM-03). */
public final class TendenciaMinimosCuadrados implements IEstimadorTendencia {

    private final int ventana;

    public TendenciaMinimosCuadrados(int ventana) {
        if (ventana < 2) {
            throw new IllegalArgumentException("La ventana necesita al menos dos registros");
        }
        this.ventana = ventana;
    }

    @Override
    public double estimar(List<Double> serie) {
        List<Double> tramo = serie.subList(Math.max(0, serie.size() - ventana), serie.size());
        int n = tramo.size();
        if (n < 2) {
            throw new IllegalArgumentException("La pendiente necesita al menos dos registros");
        }
        double xMedia = (n - 1) / 2.0;
        double yMedia = tramo.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double sxx = 0;
        double sxy = 0;
        for (int i = 0; i < n; i++) {
            sxx += (i - xMedia) * (i - xMedia);
            sxy += (i - xMedia) * (tramo.get(i) - yMedia);
        }
        return sxy / sxx;
    }

    public int getVentana() { return ventana; }
}
