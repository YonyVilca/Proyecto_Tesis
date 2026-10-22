package pe.hogar.dominio.registro;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import pe.hogar.dominio.catalogo.Actividad;
import pe.hogar.dominio.catalogo.NivelApoyo;
import pe.hogar.dominio.comun.Auditable;
import pe.hogar.dominio.comun.Bitacora;
import pe.hogar.dominio.residentes.CuentaAcceso;
import pe.hogar.dominio.residentes.Expediente;

/**
 * Observación de una actividad de un residente (DC-03). Clase abstracta del método plantilla (PD-08):
 * aquí vive lo común a toda modalidad y cada especialización define {@link #calcularAutonomia()} y
 * {@link #esPuntuable()}. Solo {@link FabricaRegistro} crea instancias (PD-12).
 */
public abstract class Registro implements Auditable {

    private final UUID codigo;
    private final Actividad actividad;
    private final Expediente expediente;
    private final TurnoDeclarado turno;
    private final LocalDateTime inicioCaptura;
    private final OrigenRegistro origen;
    private LocalDateTime fechaHora;
    private LocalDateTime finCaptura;
    private EstadoRegistro estado = EstadoRegistro.A_MEDIAS;
    private Oportunidad oportunidad = Oportunidad.SI;
    private MotivoSinOportunidad motivoSinOportunidad;
    private String motivoOtro;
    private boolean noQuiso;
    private String nota;
    private NivelApoyo nivel;
    private String motivoAnulacion;
    private final Set<CampoPrecarga> camposModificados = EnumSet.noneOf(CampoPrecarga.class);

    Registro(UUID codigo, Actividad actividad, Expediente expediente, TurnoDeclarado turno, LocalDateTime inicioCaptura,
             OrigenRegistro origen) {
        this.codigo = Objects.requireNonNull(codigo);
        this.actividad = Objects.requireNonNull(actividad);
        this.expediente = Objects.requireNonNull(expediente);
        this.turno = Objects.requireNonNull(turno);
        this.inicioCaptura = Objects.requireNonNull(inicioCaptura);
        this.origen = Objects.requireNonNull(origen);
        this.fechaHora = inicioCaptura;
    }

    /** RB-05. Nula si el registro no es puntuable. */
    public abstract Double calcularAutonomia();

    /** RB-09. Solo los registros puntuables entran en el análisis. */
    public abstract boolean esPuntuable();

    /**
     * RB-03, D-61. La oportunidad llega precargada en «sí» (D-41); si el cuidador la cambia, el campo
     * queda marcado. «No» exige un motivo, y el texto libre solo acompaña a OTRO.
     */
    public void indicarOportunidad(Oportunidad oportunidad, MotivoSinOportunidad motivo, String motivoOtro) {
        exigirEstado(EstadoRegistro.A_MEDIAS);
        Objects.requireNonNull(oportunidad);
        if (oportunidad == Oportunidad.NO && motivo == null) {
            throw new IllegalArgumentException("Sin oportunidad exige un motivo");
        }
        if (oportunidad == Oportunidad.SI && motivo != null) {
            throw new IllegalArgumentException("Con oportunidad no hay motivo");
        }
        if (motivoOtro != null && motivo != MotivoSinOportunidad.OTRO) {
            throw new IllegalArgumentException("El texto libre solo acompaña al motivo OTRO");
        }
        if (oportunidad != this.oportunidad) {
            marcarCampoModificado(CampoPrecarga.OPORTUNIDAD);
        }
        this.oportunidad = oportunidad;
        this.motivoSinOportunidad = motivo;
        this.motivoOtro = motivoOtro;
        if (oportunidad == Oportunidad.NO) {
            this.noQuiso = false;
        }
    }

    /** D-44. Hubo oportunidad y el residente no quiso; rige en los tres ejes. */
    public void marcarNoQuiso(String nota) {
        exigirEstado(EstadoRegistro.A_MEDIAS);
        if (oportunidad == Oportunidad.NO) {
            throw new IllegalStateException("«No quiso» supone que hubo oportunidad");
        }
        this.noQuiso = true;
        this.nota = nota;
    }

    /**
     * RF-17, RF-98. Cierra la captura. Con oportunidad y sin negativa el nivel es obligatorio; en otro
     * caso no se acepta nivel (RB-03, RF-70).
     */
    public void confirmar(NivelApoyo nivel, String nota, LocalDateTime fin) {
        exigirEstado(EstadoRegistro.A_MEDIAS);
        Objects.requireNonNull(fin);
        boolean requiereNivel = oportunidad == Oportunidad.SI && !noQuiso;
        if (requiereNivel && nivel == null) {
            throw new IllegalArgumentException("Con oportunidad el nivel de apoyo es obligatorio");
        }
        if (!requiereNivel && nivel != null) {
            throw new IllegalArgumentException("Sin oportunidad o con «no quiso» no hay nivel de apoyo");
        }
        if (fin.isBefore(inicioCaptura)) {
            throw new IllegalArgumentException("La captura no puede terminar antes de empezar");
        }
        this.nivel = nivel;
        if (nota != null) {
            this.nota = nota;
        }
        this.finCaptura = fin;
        this.estado = EstadoRegistro.CONFIRMADO;
    }

    /** RF-94. El registro anulado se conserva, fuera de los cálculos, y nunca se elimina (PD-15). */
    public Bitacora anular(String motivo, CuentaAcceso autor) {
        exigirEstado(EstadoRegistro.CONFIRMADO);
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("La anulación exige un motivo");
        }
        this.estado = EstadoRegistro.ANULADO;
        this.motivoAnulacion = motivo;
        return Bitacora.asentar(this, "estado", EstadoRegistro.CONFIRMADO.name(),
                EstadoRegistro.ANULADO.name(), motivo, autor);
    }

    /** Solo el registro y sus especializaciones marcan un campo, y solo si cambió (D-59). */
    protected void marcarCampoModificado(CampoPrecarga campo) {
        camposModificados.add(campo);
    }

    protected void exigirEstado(EstadoRegistro esperado) {
        if (estado != esperado) {
            throw new IllegalStateException("El registro está " + estado + " y se esperaba " + esperado);
        }
    }

    /** Lo común de esPuntuable: confirmado, con oportunidad, sin negativa y con nivel (RB-09). */
    protected boolean cumpleCondicionesComunes() {
        return estado == EstadoRegistro.CONFIRMADO && oportunidad == Oportunidad.SI && !noQuiso && nivel != null;
    }

    @Override public UUID obtenerIdentificador() { return codigo; }
    @Override public String obtenerTipoEntidad() { return "registro"; }

    public UUID getCodigo() { return codigo; }
    public Actividad getActividad() { return actividad; }
    public Expediente getExpediente() { return expediente; }
    public TurnoDeclarado getTurno() { return turno; }
    public CuentaAcceso getAutor() { return turno.getCuenta(); }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public LocalDateTime getInicioCaptura() { return inicioCaptura; }
    public LocalDateTime getFinCaptura() { return finCaptura; }
    public OrigenRegistro getOrigen() { return origen; }
    public EstadoRegistro getEstado() { return estado; }
    public Oportunidad getOportunidad() { return oportunidad; }
    public MotivoSinOportunidad getMotivoSinOportunidad() { return motivoSinOportunidad; }
    public String getMotivoOtro() { return motivoOtro; }
    public boolean isNoQuiso() { return noQuiso; }
    public String getNota() { return nota; }
    public NivelApoyo getNivel() { return nivel; }
    public String getMotivoAnulacion() { return motivoAnulacion; }
    public Set<CampoPrecarga> getCamposModificados() { return Collections.unmodifiableSet(camposModificados); }
}
