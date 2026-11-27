package pe.hogar.aplicacion;

import java.time.LocalDate;
import java.util.List;
import pe.hogar.dominio.residentes.Expediente;

/** Consulta nombres visibles desde la vista autorizada (RF-100, RNF-25). */
public interface RepositorioExpedientes {
    List<Expediente> obtenerExpedientesVisibles(LocalDate fecha);
}
