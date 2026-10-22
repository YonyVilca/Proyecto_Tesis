package pe.hogar.dominio.comun;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import pe.hogar.dominio.residentes.CuentaAcceso;

/** Asiento inmutable de un cambio: autor, fecha, valor anterior y motivo (PD-14, RNF-24). */
public final class Bitacora {

    private final UUID codigo;
    private final String tipoEntidad;
    private final UUID identificadorEntidad;
    private final String campo;
    private final String valorAnterior;
    private final String valorNuevo;
    private final String motivo;
    private final LocalDateTime fechaHora;
    private final CuentaAcceso autor;

    private Bitacora(Auditable entidad, String campo, String anterior, String nuevo, String motivo,
                     CuentaAcceso autor) {
        this.codigo = UUID.randomUUID();
        this.tipoEntidad = entidad.obtenerTipoEntidad();
        this.identificadorEntidad = entidad.obtenerIdentificador();
        this.campo = Objects.requireNonNull(campo);
        this.valorAnterior = anterior;
        this.valorNuevo = nuevo;
        this.motivo = motivo;
        this.fechaHora = LocalDateTime.now(ZoneId.of("America/Lima"));
        this.autor = Objects.requireNonNull(autor);
    }

    /** El motivo puede ser nulo: la corrección dentro de la ventana no lo pide (D-43). */
    public static Bitacora asentar(Auditable entidad, String campo, String anterior, String nuevo,
                                   String motivo, CuentaAcceso autor) {
        return new Bitacora(entidad, campo, anterior, nuevo, motivo, autor);
    }

    public UUID getCodigo() { return codigo; }
    public String getTipoEntidad() { return tipoEntidad; }
    public UUID getIdentificadorEntidad() { return identificadorEntidad; }
    public String getCampo() { return campo; }
    public String getValorAnterior() { return valorAnterior; }
    public String getValorNuevo() { return valorNuevo; }
    public String getMotivo() { return motivo; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public CuentaAcceso getAutor() { return autor; }
}
