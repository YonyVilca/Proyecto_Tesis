package pe.hogar.aplicacion;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import pe.hogar.dominio.residentes.ActividadResidente;

/** Lectura de tramos del plan que se cruzan con el período (RB-04, DC-04). */
public interface RepositorioPlanResidente {
    List<ActividadResidente> obtenerActividades(UUID expediente, LocalDate desde, LocalDate hasta);
}
