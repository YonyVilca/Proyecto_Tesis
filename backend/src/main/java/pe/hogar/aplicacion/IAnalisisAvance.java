package pe.hogar.aplicacion;

import java.util.List;
import pe.hogar.dominio.analisis.IndicadorActividad;
import pe.hogar.dominio.comun.Periodo;
import pe.hogar.dominio.residentes.Expediente;

/** Consulta indicadores y sus fundamentos para un expediente y un mes (RF-53, RF-99, SD-07, DD-57). */
public interface IAnalisisAvance {
    List<IndicadorActividad> calcularIndicadores(Expediente expediente, Periodo periodo);
}
