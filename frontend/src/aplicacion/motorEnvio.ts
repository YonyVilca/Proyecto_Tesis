import type { DatosTurnoLocal, IAlmacenLocal, TurnoLocal } from '../infraestructura/almacenLocal';
import type { RegistroDatos } from '../dominio/registro';

export interface IEnvio {
  enviarPendientes(): Promise<void>;
}

export interface TransporteTurnos {
  enviarTurno(turno: TurnoLocal): Promise<string>;
  obtenerDatos(codigo: string): Promise<DatosTurnoLocal>;
  enviarRegistro?(registro: RegistroDatos): Promise<string>;
}

/** Procesa turnos pendientes de forma serial y repetible (DC-06, AD-05, RF-29, RF-32). */
export class MotorEnvio implements IEnvio {
  private ejecucion?: Promise<void>;

  constructor(private readonly almacen: IAlmacenLocal, private readonly transporte: TransporteTurnos) { }

  enviarPendientes(): Promise<void> {
    if (this.ejecucion) return this.ejecucion;
    this.ejecucion = this.procesarTurnos().finally(() => { this.ejecucion = undefined; });
    return this.ejecucion;
  }

  private async procesarTurnos(): Promise<void> {
    for (const turno of await this.almacen.obtenerTurnosPendientes()) {
      try {
        const resultado = await this.transporte.enviarTurno(turno);
        if (resultado === 'ACEPTADO' || resultado === 'DUPLICADO') {
          const datos = await this.transporte.obtenerDatos(turno.codigo);
          await this.almacen.guardarDatosTurno(datos);
          await this.almacen.marcarTurnoEnviado(turno.codigo);
        } else if (resultado === 'RECHAZADO_VALIDACION') {
          await this.almacen.marcarTurnoRechazado(turno.codigo);
        }
      } catch { /* Un error de red conserva el turno pendiente para el siguiente intento. */ }
    }
    if (!this.transporte.enviarRegistro) return;
    for (const registro of await this.almacen.obtenerPendientes()) {
      try {
        const turno = await this.almacen.obtenerTurno(registro.turno);
        if (!turno || turno.pendiente || turno.rechazado) continue;
        const resultado = await this.transporte.enviarRegistro(registro);
        if (resultado === 'ACEPTADO' || resultado === 'DUPLICADO') await this.almacen.marcarRegistroEnviado(registro.codigo);
        else if (resultado === 'RECHAZADO_FUERA_DE_VENTANA' || resultado === 'RECHAZADO_VALIDACION') {
          await this.almacen.marcarRegistroRechazado(registro.codigo, resultado);
        }
      } catch { /* Red interrumpida o rechazo sin turno: queda para otro intento. */ }
    }
  }
}
