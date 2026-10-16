package pe.hogar.dominio.comun;

import java.util.UUID;

/** Entidad cuyos cambios se asientan en la bitácora (PD-14, RNF-24). */
public interface Auditable {

    UUID obtenerIdentificador();

    String obtenerTipoEntidad();
}
