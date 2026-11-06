import type { MotivoSinOportunidad, RegistroDatos } from '../dominio/registro';
import type { DatosTurnoLocal, TurnoLocal } from '../infraestructura/almacenLocal';

export interface EntradaCaptura {
  codigo: string; expediente: string; actividad: string; versionPlantilla: number;
  pasos: Array<{ orden: number; cumplido: boolean }>; oportunidad: boolean;
  motivoSinOportunidad?: MotivoSinOportunidad; motivoOtro?: string; noQuiso: boolean;
  nota?: string; nivel?: string; secuenciaCorrecta?: boolean; secuenciaRelevante: boolean;
}
export interface PrecargaCaptura {
  codigo?: string; expediente: string; actividad: string; versionPlantilla: number;
  pasos: Array<{ orden: number; cumplido: boolean }>; secuenciaCorrecta?: boolean; oportunidad?: boolean;
}

/** Residentes a los que corresponde la actividad elegida (AD-06, RF-18). */
export function residentesPara(datos: DatosTurnoLocal, actividad: string): DatosTurnoLocal['expedientes'] {
  const asignados = new Set(datos.actividadesResidente
    .filter((item) => item.actividad === actividad && item.estado !== 'NO_CORRESPONDE')
    .map((item) => item.expediente));
  return datos.expedientes.filter((residente) => asignados.has(residente.codigo));
}

/** Construye un registro para un residente y compara únicamente su precarga compatible (AD-06, RF-18, RF-32, RF-70, RF-98). */
export function construirRegistro(entrada: EntradaCaptura, precarga: PrecargaCaptura | undefined,
  turno: TurnoLocal, inicio: Date, fin: Date): RegistroDatos {
  const compatible = precarga?.expediente === entrada.expediente && precarga.actividad === entrada.actividad
    && precarga.versionPlantilla === entrada.versionPlantilla;
  const completa = entrada.oportunidad && !entrada.noQuiso;
  const pasos = completa ? structuredClone(entrada.pasos) : [];
  const secuenciaCorrecta = completa && entrada.secuenciaRelevante ? entrada.secuenciaCorrecta : undefined;
  const modificados: string[] = [];
  if (!entrada.oportunidad) modificados.push('OPORTUNIDAD');
  if (completa && compatible && JSON.stringify(precarga.pasos) !== JSON.stringify(pasos)) modificados.push('PASOS');
  if (completa && compatible && entrada.secuenciaRelevante && precarga.secuenciaCorrecta !== secuenciaCorrecta) modificados.push('SECUENCIA');
  return { codigo: entrada.codigo, turno: turno.codigo, expediente: entrada.expediente, actividad: entrada.actividad,
    versionPlantilla: entrada.versionPlantilla, inicioCaptura: inicio.toISOString(), finCaptura: fin.toISOString(), origen: 'TACTIL',
    oportunidad: entrada.oportunidad, motivoSinOportunidad: entrada.oportunidad ? undefined : entrada.motivoSinOportunidad,
    motivoOtro: !entrada.oportunidad && entrada.motivoSinOportunidad === 'OTRO' ? entrada.motivoOtro : undefined,
    noQuiso: entrada.oportunidad && entrada.noQuiso, nota: entrada.nota || undefined,
    nivel: completa ? entrada.nivel : undefined, pasos, secuenciaCorrecta, camposModificados: modificados, pendiente: true };
}
