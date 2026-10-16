package pe.hogar.dominio.registro;

import java.time.LocalDateTime;
import java.util.List;
import pe.hogar.dominio.catalogo.Actividad;
import pe.hogar.dominio.catalogo.PlantillaPasos;
import pe.hogar.dominio.residentes.Expediente;

/** Registro de una actividad por pasos, con la versión de plantilla vigente al crearse (DC-03). */
public final class RegistroPorPasos extends Registro {

    private final int versionPlantilla;
    private final List<RegistroPaso> pasos;
    private Boolean secuenciaCorrecta;
    private boolean[] pasosPrecargados;
    private Boolean secuenciaPrecargada;

    RegistroPorPasos(Actividad actividad, Expediente expediente, TurnoDeclarado turno,
                     LocalDateTime inicioCaptura, OrigenRegistro origen, PlantillaPasos plantilla) {
        super(actividad, expediente, turno, inicioCaptura, origen);
        this.versionPlantilla = plantilla.getVersion();
        this.pasos = plantilla.obtenerPasos().stream().map(RegistroPaso::new).toList();
    }

    /**
     * D-41. Copia pasos y secuencia del último registro del residente en la actividad, si es de la misma
     * versión de plantilla. El nivel de apoyo nunca se copia.
     */
    public void precargarDesde(RegistroPorPasos anterior) {
        exigirEstado(EstadoRegistro.A_MEDIAS);
        if (anterior == null || anterior.versionPlantilla != versionPlantilla) {
            return;
        }
        pasosPrecargados = new boolean[pasos.size()];
        for (int i = 0; i < pasos.size(); i++) {
            boolean valor = anterior.pasos.get(i).isCumplido();
            pasos.get(i).setCumplido(valor);
            pasosPrecargados[i] = valor;
        }
        secuenciaCorrecta = anterior.secuenciaCorrecta;
        secuenciaPrecargada = anterior.secuenciaCorrecta;
    }

    /** RF-20. Si el valor difiere del precargado, queda la marca de campo modificado (D-59). */
    public void marcarPaso(int orden, boolean cumplido) {
        exigirEstado(EstadoRegistro.A_MEDIAS);
        if (orden < 1 || orden > pasos.size()) {
            throw new IllegalArgumentException("La plantilla v" + versionPlantilla + " no tiene el paso " + orden);
        }
        pasos.get(orden - 1).setCumplido(cumplido);
        if (pasosPrecargados != null && pasosPrecargados[orden - 1] != cumplido) {
            marcarCampoModificado(CampoPrecarga.PASOS);
        }
    }

    /** RF-76. Solo para actividades cuya secuencia es relevante. */
    public void indicarSecuencia(boolean correcta) {
        exigirEstado(EstadoRegistro.A_MEDIAS);
        if (!getActividad().isSecuenciaRelevante()) {
            throw new IllegalStateException("La actividad no evalúa la secuencia");
        }
        if (secuenciaPrecargada != null && secuenciaPrecargada != correcta) {
            marcarCampoModificado(CampoPrecarga.SECUENCIA);
        }
        secuenciaCorrecta = correcta;
    }

    /** RB-05: (pasos cumplidos / pasos totales) × peso del nivel. */
    @Override
    public Double calcularAutonomia() {
        if (!esPuntuable()) {
            return null;
        }
        return (double) getPasosCumplidos() / getPasosTotales() * getNivel().peso();
    }

    @Override
    public boolean esPuntuable() {
        return cumpleCondicionesComunes();
    }

    public int getPasosCumplidos() {
        return (int) pasos.stream().filter(RegistroPaso::isCumplido).count();
    }

    public int getPasosTotales() {
        return pasos.size();
    }

    public int getVersionPlantilla() { return versionPlantilla; }
    public List<RegistroPaso> getPasos() { return pasos; }
    public Boolean getSecuenciaCorrecta() { return secuenciaCorrecta; }
}
