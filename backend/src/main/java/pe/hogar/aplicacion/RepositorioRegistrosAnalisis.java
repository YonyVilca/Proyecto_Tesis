package pe.hogar.aplicacion;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import pe.hogar.dominio.registro.Registro;

/** Lectura de registros confirmados necesarios para la serie del análisis (RF-75, RB-09, SD-07). */
public interface RepositorioRegistrosAnalisis {
    List<Registro> obtenerConfirmados(UUID expediente, LocalDate hasta);
}
