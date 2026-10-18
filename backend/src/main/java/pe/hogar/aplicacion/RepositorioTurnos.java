package pe.hogar.aplicacion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pe.hogar.dominio.registro.TipoTurno;
import pe.hogar.dominio.registro.TurnoDeclarado;
import pe.hogar.dominio.residentes.CuentaAcceso;

/** Persistencia de turnos declarados y sus encadenamientos (DC-03, PD-03). */
public interface RepositorioTurnos {
    void bloquearDeclaracion(UUID codigo, CuentaAcceso cuenta);
    Optional<TurnoDeclarado> buscarPorCodigo(UUID codigo);
    boolean existeSolapamiento(UUID codigo, CuentaAcceso cuenta, LocalDateTime inicio, LocalDateTime fin);
    void insertar(TurnoDeclarado turno);
    void agregarEncadenados(UUID codigo, int siguienteOrden, List<TipoTurno> nuevos, LocalDateTime finVentana);
}
