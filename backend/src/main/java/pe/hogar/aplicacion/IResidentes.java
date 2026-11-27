package pe.hogar.aplicacion;

import java.time.LocalDate;
import java.util.List;
import pe.hogar.dominio.residentes.Expediente;

/** Lectura de residentes visibles y ordenados por nombre (RF-99, RF-100, RNF-25, DC-04). */
public interface IResidentes {
    List<Expediente> obtenerExpedientes(LocalDate fecha);
}
