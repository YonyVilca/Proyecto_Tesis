package pe.hogar.aplicacion;

import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
import pe.hogar.dominio.registro.TurnoDeclarado;
import pe.hogar.dominio.residentes.CuentaAcceso;

/** Declaración, recuperación de turnos y descarga de datos de captura (DC-06, RF-02, RF-12, RF-90, RF-101, DD-56). */
public interface IRegistro {
    ResultadoRecepcion declararTurno(TurnoDeclarado turno);
    ResultadoRecepcion recibirRegistro(RegistroRecibido recibido, CuentaAcceso autor);
    Optional<DatosTurno> obtenerDatosTurno(UUID codigo, CuentaAcceso solicitante);
    Optional<TurnoDeclarado> obtenerTurnoVigente(CuentaAcceso cuenta, LocalDateTime momento);
}
