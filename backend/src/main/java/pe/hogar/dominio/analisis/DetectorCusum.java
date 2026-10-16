package pe.hogar.dominio.analisis;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import pe.hogar.dominio.comun.Periodo;
import pe.hogar.dominio.registro.Registro;

/**
 * Suma acumulada (CUSUM) unilateral inferior sobre la autonomía (RB-06, AD-03). Detecta caídas
 * sostenidas respecto de una referencia mu0, tolerando oscilaciones menores que k.
 */
public final class DetectorCusum implements IDetectorRetroceso {

    private final double k;
    private final double h;
    private final int minimoRegistros;

    public DetectorCusum(double k, double h, int minimoRegistros) {
        if (k < 0 || h <= 0 || minimoRegistros < 1) {
            throw new IllegalArgumentException("Parámetros de la CUSUM fuera de rango");
        }
        this.k = k;
        this.h = h;
        this.minimoRegistros = minimoRegistros;
    }

    @Override
    public Optional<EvaluacionRetroceso> detectar(List<Registro> segmento, Periodo periodo) {
        Optional<Referencia> referencia = calcularReferencia(segmento, periodo);
        if (referencia.isEmpty()) {
            return Optional.empty();
        }
        Referencia ref = referencia.get();
        List<Registro> evaluados = new ArrayList<>();
        for (Registro r : segmento.subList(ref.desde(), segmento.size())) {
            if (periodo.contiene(r.getFechaHora().toLocalDate())) {
                evaluados.add(r);
            }
        }
        if (evaluados.isEmpty()) {
            return Optional.empty();
        }
        Excursion ex = aplicarCusum(evaluados.stream().map(Registro::calcularAutonomia).toList(), ref.mu0());
        if (!ex.supera()) {
            return Optional.of(new EvaluacionRetroceso(ref.mu0(), ex.valor(), null));
        }
        List<Registro> origen = evaluados.subList(ex.inicio(), ex.fin() + 1);
        String texto = "Las caídas respecto de " + Formato.decimales(ref.mu0(), 2) + " (" + ref.como()
                + ") suman " + Formato.decimales(ex.valor(), 2) + ", sobre el umbral " + Formato.decimales(h, 2)
                + ", en " + origen.size() + " registros.";
        return Optional.of(new EvaluacionRetroceso(ref.mu0(), ex.valor(),
                new Fundamento("RB-06 retrocede", h, texto, origen)));
    }

    /**
     * EM-05. La referencia es, en este orden: la media del mes previo, la de los últimos registros
     * anteriores al mes o la de los primeros registros del segmento.
     */
    private Optional<Referencia> calcularReferencia(List<Registro> segmento, Periodo periodo) {
        Periodo previo = periodo.anterior();
        List<Double> anteriores = new ArrayList<>();
        List<Double> delPrevio = new ArrayList<>();
        List<Integer> actuales = new ArrayList<>();
        for (int i = 0; i < segmento.size(); i++) {
            var fecha = segmento.get(i).getFechaHora().toLocalDate();
            double a = segmento.get(i).calcularAutonomia();
            if (fecha.isBefore(periodo.inicio())) {
                anteriores.add(a);
            }
            if (previo.contiene(fecha)) {
                delPrevio.add(a);
            }
            if (periodo.contiene(fecha)) {
                actuales.add(i);
            }
        }
        if (actuales.isEmpty()) {
            return Optional.empty();
        }
        if (delPrevio.size() >= minimoRegistros) {
            return Optional.of(new Referencia(media(delPrevio), actuales.get(0), "media del periodo previo"));
        }
        if (anteriores.size() >= minimoRegistros) {
            List<Double> ultimos = anteriores.subList(anteriores.size() - minimoRegistros, anteriores.size());
            return Optional.of(new Referencia(media(ultimos), actuales.get(0), "ultimos registros anteriores"));
        }
        if (segmento.size() > minimoRegistros) {
            int desde = Math.max(minimoRegistros, actuales.get(0));
            if (desde <= actuales.get(actuales.size() - 1)) {
                List<Double> primeros = segmento.subList(0, minimoRegistros).stream()
                        .map(Registro::calcularAutonomia).toList();
                return Optional.of(new Referencia(media(primeros), desde, "primeros registros"));
            }
        }
        return Optional.empty();
    }

    /** S(t) = max(0, S(t-1) + mu0 - a(t) - k). Se detiene en la primera vez que S supera h. */
    private Excursion aplicarCusum(List<Double> serie, double mu0) {
        double s = 0;
        double maximo = 0;
        int inicioActual = 0;
        int mejorInicio = 0;
        int mejorFin = -1;
        for (int t = 0; t < serie.size(); t++) {
            double previo = s;
            s = Math.max(0.0, s + (mu0 - serie.get(t) - k));
            if (previo == 0.0 && s > 0.0) {
                inicioActual = t;
            }
            if (s > maximo) {
                maximo = s;
                mejorInicio = inicioActual;
                mejorFin = t;
            }
            if (s > h) {
                return new Excursion(s, true, inicioActual, t);
            }
        }
        return new Excursion(maximo, false, mejorInicio, mejorFin);
    }

    private static double media(List<Double> valores) {
        return valores.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    private record Referencia(double mu0, int desde, String como) {
    }

    private record Excursion(double valor, boolean supera, int inicio, int fin) {
    }

    public double getK() { return k; }
    public double getH() { return h; }
    public int getMinimoRegistros() { return minimoRegistros; }
}
