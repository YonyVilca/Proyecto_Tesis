package pe.hogar.dominio.analisis;

/**
 * Resultado de evaluar el retroceso [A6]: la referencia mu0, el valor de la suma acumulada y, si superó
 * el umbral, el fundamento. Un fundamento nulo significa «sin retroceso» (AD-03).
 */
public record EvaluacionRetroceso(double referencia, double valor, Fundamento fundamento) {

    public boolean huboRetroceso() {
        return fundamento != null;
    }
}
