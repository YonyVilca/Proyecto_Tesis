package pe.hogar.aplicacion;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.hogar.dominio.analisis.IndicadorActividad;
import pe.hogar.dominio.analisis.ServicioAnalisis;
import pe.hogar.dominio.comun.Periodo;
import pe.hogar.dominio.residentes.ActividadResidente;
import pe.hogar.dominio.residentes.Expediente;

/** Orquesta la consulta de residentes y delega el cálculo al dominio puro (RF-99, SD-07, DD-57). */
@Service
@Transactional(readOnly = true)
public class ServicioResidentes implements IResidentes, IAnalisisAvance {
    private final RepositorioExpedientes expedientes;
    private final RepositorioPlanResidente planes;
    private final RepositorioRegistrosAnalisis registros;
    private final RepositorioParametrosAnalisis parametros;

    public ServicioResidentes(RepositorioExpedientes expedientes, RepositorioPlanResidente planes,
            RepositorioRegistrosAnalisis registros, RepositorioParametrosAnalisis parametros) {
        this.expedientes = expedientes;
        this.planes = planes;
        this.registros = registros;
        this.parametros = parametros;
    }

    /** RF-99: solo expedientes activos, ya ordenados por nombre visible desde la vista autorizada. */
    @Override
    public List<Expediente> obtenerExpedientes(LocalDate fecha) {
        return expedientes.obtenerExpedientesVisibles(fecha);
    }

    /** SD-07: carga el plan, los registros confirmados y los parámetros sin inyectar las estrategias ampliadas. */
    @Override
    public List<IndicadorActividad> calcularIndicadores(Expediente expediente, Periodo periodo) {
        List<ActividadResidente> plan = planes.obtenerActividades(expediente.getCodigo(), periodo.inicio(), periodo.fin());
        var serie = registros.obtenerConfirmados(expediente.getCodigo(), periodo.fin());
        Map<String, Double> valores = parametros.obtenerVigentes();
        return new ServicioAnalisis(valores).calcularIndicadores(expediente, periodo, plan, serie);
    }
}
