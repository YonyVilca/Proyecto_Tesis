package pe.hogar.dominio.registro;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import pe.hogar.dominio.residentes.CuentaAcceso;

/**
 * Turno que declara el cuidador al abrir la aplicación (D-42). Fija la ventana en la que puede
 * registrar (RB-11). Las horas son de Lima (D-66).
 */
public final class TurnoDeclarado {

    private static final LocalTime SIETE = LocalTime.of(7, 0);
    private static final LocalTime TRECE = LocalTime.of(13, 0);
    private static final LocalTime DIECINUEVE = LocalTime.of(19, 0);

    private final UUID codigo;
    private final TipoTurno tipo;
    private final CuentaAcceso cuenta;
    private final LocalDateTime declaradoEn;
    private final LocalDateTime inicioVentana;
    private LocalDateTime finVentana;
    private final List<TipoTurno> encadenados = new ArrayList<>();

    public TurnoDeclarado(UUID codigo, TipoTurno tipo, CuentaAcceso cuenta, LocalDateTime declaradoEn) {
        this.codigo = Objects.requireNonNull(codigo);
        this.tipo = Objects.requireNonNull(tipo);
        this.cuenta = Objects.requireNonNull(cuenta);
        this.declaradoEn = Objects.requireNonNull(declaradoEn);
        LocalDateTime[] ventana = ventanaDe(tipo, declaradoEn);
        this.inicioVentana = ventana[0];
        this.finVentana = ventana[1];
    }

    /** Sugerencia por la hora: M de 7 a 13, T de 13 a 19, N el resto (RF-12). */
    public static TipoTurno sugerirTipo(LocalDateTime momento) {
        LocalTime hora = momento.toLocalTime();
        if (!hora.isBefore(SIETE) && hora.isBefore(TRECE)) {
            return TipoTurno.M;
        }
        if (!hora.isBefore(TRECE) && hora.isBefore(DIECINUEVE)) {
            return TipoTurno.T;
        }
        return TipoTurno.N;
    }

    public boolean habilitaRegistro() {
        return tipo != TipoTurno.D;
    }

    public boolean contieneMomento(LocalDateTime momento) {
        return contieneMomento(momento, 0);
    }

    /**
     * RB-11. El teléfono la usa sin tolerancia para habilitar el registro; el servidor, con 15 minutos
     * para aceptarlo (D-66). La base repite la regla con trg_registro_ventana (DD-46).
     */
    public boolean contieneMomento(LocalDateTime momento, int toleranciaMinutos) {
        if (!habilitaRegistro()) {
            return false;
        }
        Duration tolerancia = Duration.ofMinutes(toleranciaMinutos);
        return !momento.isBefore(inicioVentana.minus(tolerancia))
                && !momento.isAfter(finVentana.plus(tolerancia));
    }

    /** Turno contiguo del mismo cuidador: amplía la ventana sin crear otro turno (RF-90, DD-34). */
    public void encadenar(TipoTurno siguiente) {
        if (!habilitaRegistro() || siguiente == TipoTurno.D) {
            throw new IllegalStateException("El descanso no se encadena");
        }
        LocalDateTime[] ventana = ventanaDe(siguiente, finVentana);
        if (!ventana[0].equals(finVentana)) {
            throw new IllegalArgumentException("El turno " + siguiente + " no empieza donde termina la ventana");
        }
        encadenados.add(siguiente);
        finVentana = ventana[1];
    }

    /** Ventana del tipo que contiene o sigue al momento de la declaración. */
    private static LocalDateTime[] ventanaDe(TipoTurno tipo, LocalDateTime momento) {
        var dia = momento.toLocalDate();
        return switch (tipo) {
            case M -> new LocalDateTime[] {dia.atTime(SIETE), dia.atTime(TRECE)};
            case T -> new LocalDateTime[] {dia.atTime(TRECE), dia.atTime(DIECINUEVE)};
            case MT, GC, GD -> new LocalDateTime[] {dia.atTime(SIETE), dia.atTime(DIECINUEVE)};
            case N, GN -> {
                var inicio = momento.toLocalTime().isBefore(SIETE) ? dia.minusDays(1) : dia;
                yield new LocalDateTime[] {inicio.atTime(DIECINUEVE), inicio.plusDays(1).atTime(SIETE)};
            }
            case D -> new LocalDateTime[] {momento, momento};
        };
    }

    public UUID getCodigo() { return codigo; }
    public TipoTurno getTipo() { return tipo; }
    public CuentaAcceso getCuenta() { return cuenta; }
    public LocalDateTime getDeclaradoEn() { return declaradoEn; }
    public LocalDateTime getInicioVentana() { return inicioVentana; }
    public LocalDateTime getFinVentana() { return finVentana; }
    public List<TipoTurno> getEncadenados() { return Collections.unmodifiableList(encadenados); }
}
