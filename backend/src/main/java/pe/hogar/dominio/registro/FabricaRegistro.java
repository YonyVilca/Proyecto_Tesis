package pe.hogar.dominio.registro;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import pe.hogar.dominio.catalogo.Actividad;
import pe.hogar.dominio.catalogo.Modalidad;
import pe.hogar.dominio.catalogo.NivelApoyo;
import pe.hogar.dominio.residentes.CuentaAcceso;
import pe.hogar.dominio.residentes.Expediente;
import pe.hogar.dominio.residentes.Rol;

/**
 * Método fábrica parametrizado (PD-12). La especialización la decide la modalidad de la actividad, no
 * quien llama (D-25). Los constructores de Registro son de paquete: nadie más puede crearlos.
 */
public final class FabricaRegistro {

    public Registro crear(Actividad actividad, Expediente expediente, TurnoDeclarado turno,
                          LocalDateTime inicioCaptura) {
        return crear(actividad, expediente, turno, inicioCaptura, OrigenRegistro.TACTIL);
    }

    public Registro crear(Actividad actividad, Expediente expediente, TurnoDeclarado turno,
                          LocalDateTime inicioCaptura, OrigenRegistro origen) {
        return switch (actividad.getModalidad()) {
            case PASOS -> new RegistroPorPasos(UUID.randomUUID(), actividad, expediente, turno, inicioCaptura, origen,
                    actividad.obtenerPlantillaVigente().orElseThrow(
                            () -> new IllegalStateException("La actividad no tiene plantilla vigente")));
            case EVENTO -> new RegistroEvento(UUID.randomUUID(), actividad, expediente, turno, inicioCaptura, origen);
            case VALORACION -> throw new UnsupportedOperationException(
                    "La valoración se registra en la tesis final");
        };
    }

    /** Reconstruye una captura con la plantilla descargada por el teléfono (RF-65, DD-21, PD-12). */
    public Registro reconstruir(UUID codigo, Actividad actividad, Expediente expediente, TurnoDeclarado turno,
                                LocalDateTime inicioCaptura, OrigenRegistro origen, int versionPlantilla) {
        if (actividad.getModalidad() != Modalidad.PASOS) {
            throw new IllegalArgumentException("La reconstrucción con versión requiere modalidad PASOS");
        }
        var plantilla = actividad.obtenerPlantilla(versionPlantilla)
                .orElseThrow(() -> new IllegalArgumentException("No existe la versión de plantilla " + versionPlantilla));
        return new RegistroPorPasos(codigo, actividad, expediente, turno, inicioCaptura, origen, plantilla);
    }

    /** Reconstrucción de una captura confirmada para análisis (SD-07, RF-53). */
    public Registro reconstruir(UUID codigo, Actividad actividad, Expediente expediente, TurnoDeclarado turno,
            LocalDateTime inicioCaptura, OrigenRegistro origen, int versionPlantilla,
            LocalDateTime fechaHora, LocalDateTime finCaptura, Oportunidad oportunidad,
            MotivoSinOportunidad motivo, boolean noQuiso, NivelApoyo nivel, List<Boolean> pasos,
            Boolean secuencia, Boolean participo, boolean anulado, String motivoAnulacion) {
        return reconstruir(codigo, actividad, expediente, turno, inicioCaptura, origen, versionPlantilla,
                fechaHora, finCaptura, oportunidad, motivo, null, noQuiso, nivel, pasos, secuencia,
                participo, anulado, motivoAnulacion);
    }

    /** Reconstrucción completa que conserva el detalle opcional del motivo OTRO (SD-07, RF-53). */
    public Registro reconstruir(UUID codigo, Actividad actividad, Expediente expediente, TurnoDeclarado turno,
            LocalDateTime inicioCaptura, OrigenRegistro origen, int versionPlantilla,
            LocalDateTime fechaHora, LocalDateTime finCaptura, Oportunidad oportunidad,
            MotivoSinOportunidad motivo, String motivoOtro, boolean noQuiso, NivelApoyo nivel,
            List<Boolean> pasos, Boolean secuencia, Boolean participo, boolean anulado, String motivoAnulacion) {
        Registro registro;
        if (actividad.getModalidad() == Modalidad.PASOS) {
            RegistroPorPasos porPasos = (RegistroPorPasos) reconstruir(codigo, actividad, expediente, turno,
                    inicioCaptura, origen, versionPlantilla);
            if (pasos != null) for (int i = 0; i < pasos.size(); i++) porPasos.marcarPaso(i + 1, pasos.get(i));
            if (secuencia != null) porPasos.indicarSecuencia(secuencia);
            registro = porPasos;
        } else if (actividad.getModalidad() == Modalidad.EVENTO) {
            registro = new RegistroEvento(codigo, actividad, expediente, turno, inicioCaptura, origen);
            if (participo != null) ((RegistroEvento) registro).indicarParticipacion(participo);
        } else {
            throw new UnsupportedOperationException("La valoración se registra en la tesis final");
        }
        registro.restaurarFechaHora(fechaHora);
        if (oportunidad == Oportunidad.NO) registro.indicarOportunidad(oportunidad, motivo, motivoOtro);
        if (noQuiso) registro.marcarNoQuiso(null);
        registro.confirmar(nivel, null, finCaptura);
        if (anulado) {
            CuentaAcceso coordinadora = new CuentaAcceso("SISTEMA", "Sistema", "sistema", "hash",
                    Rol.COORDINADORA, true);
            registro.anular(motivoAnulacion, coordinadora);
        }
        return registro;
    }
}
