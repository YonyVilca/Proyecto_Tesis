package pe.hogar.dominio.registro;

import java.util.Objects;
import pe.hogar.dominio.catalogo.Paso;

/** Cumplimiento de un paso dentro de un registro por pasos. */
public final class RegistroPaso {

    private final Paso paso;
    private boolean cumplido;

    RegistroPaso(Paso paso) {
        this.paso = Objects.requireNonNull(paso);
    }

    void setCumplido(boolean cumplido) {
        this.cumplido = cumplido;
    }

    public int getOrden() { return paso.orden(); }
    public Paso getPaso() { return paso; }
    public boolean isCumplido() { return cumplido; }
}
