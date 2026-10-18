package pe.hogar.aplicacion;

import java.time.LocalDate;

/** Lectura de la descarga operativa vigente para la fecha del turno (DD-43, PD-06). */
public interface ConsultaDatosTurno {
    DatosTurno obtener(LocalDate fechaTurno);
}
