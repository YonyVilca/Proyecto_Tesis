package pe.hogar.aplicacion;

import java.util.Optional;
import java.util.UUID;
import pe.hogar.dominio.registro.TurnoDeclarado;
import pe.hogar.dominio.residentes.CuentaAcceso;

/** Declaración de turnos y descarga de datos de captura (DC-06, RF-12, RF-101). */
public interface IRegistro {
    ResultadoRecepcion declararTurno(TurnoDeclarado turno);
    Optional<DatosTurno> obtenerDatosTurno(UUID codigo, CuentaAcceso solicitante);
}
