package pe.hogar.aplicacion;

import java.util.Map;

/** Lee los valores vigentes de comun.parametro sin alterarlos (RB-10). */
public interface RepositorioParametrosAnalisis {
    Map<String, Double> obtenerVigentes();
}
