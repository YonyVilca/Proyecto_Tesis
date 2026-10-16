package pe.hogar.dominio.analisis;

import java.util.Objects;
import pe.hogar.dominio.catalogo.Actividad;

/** Resultado del análisis de una actividad en un mes. Se calcula en cada consulta (EM-01). */
public final class IndicadorActividad {

    private final Actividad actividad;
    private final int registrosConsiderados;
    private final Double avance;
    private final Double autonomia;
    private final boolean datosSuficientes;
    private final int negativas;
    private final boolean tienePasosCriticos;
    private int registrosConsecutivosDominio;
    private Double pendiente;
    private Double referenciaCusum;
    private Double valorCusum;
    private EstadoVisual estadoVisual = EstadoVisual.SIN_DATOS;
    private Fundamento fundamento;

    IndicadorActividad(Actividad actividad, int registrosConsiderados, Double avance, Double autonomia,
                       boolean datosSuficientes, int negativas, boolean tienePasosCriticos) {
        this.actividad = Objects.requireNonNull(actividad);
        this.registrosConsiderados = registrosConsiderados;
        this.avance = avance;
        this.autonomia = autonomia;
        this.datosSuficientes = datosSuficientes;
        this.negativas = negativas;
        this.tienePasosCriticos = tienePasosCriticos;
    }

    public Fundamento obtenerFundamento() {
        return fundamento;
    }

    /** [A7] Solo si domina y la actividad no tiene pasos críticos (RF-77). */
    public boolean admiteRetiroApoyo() {
        return estadoVisual == EstadoVisual.DOMINA && !tienePasosCriticos;
    }

    public Double getNecesidadApoyo() {
        return autonomia == null ? null : 1 - autonomia;
    }

    void setRegistrosConsecutivosDominio(int n) { this.registrosConsecutivosDominio = n; }
    void setPendiente(Double pendiente) { this.pendiente = pendiente; }
    void setReferenciaCusum(Double referencia) { this.referenciaCusum = referencia; }
    void setValorCusum(Double valor) { this.valorCusum = valor; }
    void establecerEstado(EstadoVisual estado, Fundamento fundamento) {
        this.estadoVisual = Objects.requireNonNull(estado);
        this.fundamento = Objects.requireNonNull(fundamento);
    }

    public Actividad getActividad() { return actividad; }
    public int getRegistrosConsiderados() { return registrosConsiderados; }
    public Double getAvance() { return avance; }
    public Double getAutonomia() { return autonomia; }
    public boolean isDatosSuficientes() { return datosSuficientes; }
    public int getNegativas() { return negativas; }
    public boolean isTienePasosCriticos() { return tienePasosCriticos; }
    public int getRegistrosConsecutivosDominio() { return registrosConsecutivosDominio; }
    public Double getPendiente() { return pendiente; }
    public Double getReferenciaCusum() { return referenciaCusum; }
    public Double getValorCusum() { return valorCusum; }
    public EstadoVisual getEstadoVisual() { return estadoVisual; }
}
