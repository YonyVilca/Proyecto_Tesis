package pe.hogar.dominio.analisis;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import pe.hogar.dominio.registro.TipoTurno;

/** Registros efectivos frente a los esperados de una actividad en el mes (RB-08, RF-49). */
public final class Cobertura {

    private final int registrosEsperados;
    private final int registrosEfectivos;
    private final boolean suficiente;
    private final Map<TipoTurno, Integer> repartoPorTurno;

    public Cobertura(int registrosEsperados, int registrosEfectivos, boolean suficiente,
                     Map<TipoTurno, Integer> repartoPorTurno) {
        this.registrosEsperados = registrosEsperados;
        this.registrosEfectivos = registrosEfectivos;
        this.suficiente = suficiente;
        this.repartoPorTurno = Collections.unmodifiableMap(new EnumMap<>(repartoPorTurno));
    }

    /** Nulo cuando no hay esperados: la suficiencia se juzga por cantidad de registros (D-62). */
    public Double getPorcentaje() {
        return registrosEsperados == 0 ? null : (double) registrosEfectivos / registrosEsperados;
    }

    public int getRegistrosEsperados() { return registrosEsperados; }
    public int getRegistrosEfectivos() { return registrosEfectivos; }
    public boolean isSuficiente() { return suficiente; }
    public Map<TipoTurno, Integer> getRepartoPorTurno() { return repartoPorTurno; }
}
