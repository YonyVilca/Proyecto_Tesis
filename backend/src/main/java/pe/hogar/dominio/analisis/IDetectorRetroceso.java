package pe.hogar.dominio.analisis;

import java.util.List;
import java.util.Optional;
import pe.hogar.dominio.comun.Periodo;
import pe.hogar.dominio.registro.Registro;

/** Estrategia de detección de retroceso [A6] (PD-11). Sin ella, el núcleo nunca muestra RETROCEDE. */
public interface IDetectorRetroceso {

    /**
     * Evalúa el segmento de la versión vigente, ordenado en el tiempo. Vacío si no es evaluable: no hay
     * registros del mes o no hay referencia (AD-03).
     */
    Optional<EvaluacionRetroceso> detectar(List<Registro> segmento, Periodo periodo);
}
