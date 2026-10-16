package pe.hogar.dominio.residentes;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import pe.hogar.dominio.catalogo.Actividad;
import pe.hogar.dominio.comun.Auditable;

/** Estado de una actividad para un residente durante una vigencia (plan del residente). */
public final class ActividadResidente implements Auditable {

    private final UUID codigo;
    private final Actividad actividad;
    private final EstadoActividadResidente estado;
    private final LocalDate vigenciaDesde;
    private final LocalDate vigenciaHasta;

    public ActividadResidente(UUID codigo, Actividad actividad, EstadoActividadResidente estado,
                              LocalDate vigenciaDesde, LocalDate vigenciaHasta) {
        this.codigo = Objects.requireNonNull(codigo);
        this.actividad = Objects.requireNonNull(actividad);
        this.estado = Objects.requireNonNull(estado);
        this.vigenciaDesde = Objects.requireNonNull(vigenciaDesde);
        if (vigenciaHasta != null && vigenciaHasta.isBefore(vigenciaDesde)) {
            throw new IllegalArgumentException("La vigencia termina antes de empezar");
        }
        this.vigenciaHasta = vigenciaHasta;
    }

    public boolean esPrioritaria() {
        return estado == EstadoActividadResidente.PRIORITARIA;
    }

    @Override public UUID obtenerIdentificador() { return codigo; }
    @Override public String obtenerTipoEntidad() { return "actividad_residente"; }

    public Actividad getActividad() { return actividad; }
    public EstadoActividadResidente getEstado() { return estado; }
    public LocalDate getVigenciaDesde() { return vigenciaDesde; }
    public LocalDate getVigenciaHasta() { return vigenciaHasta; }
}
