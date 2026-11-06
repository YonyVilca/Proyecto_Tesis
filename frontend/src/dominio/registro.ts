/** Reglas de captura por pasos del dominio (RF-17–RF-21, RF-70, RF-98, D-41, D-59, D-66). */
export type MotivoSinOportunidad = 'NORMA_DEL_HOGAR' | 'FALTAN_RECURSOS' | 'CUIDADOR_CON_OTRO_RESIDENTE' | 'NO_ESTABA_EN_EL_HOGAR' | 'ENFERMO_O_INDISPUESTO' | 'OTRO';
export type NivelApoyo = string;
export interface PasoRegistro { orden: number; cumplido: boolean }

export interface RegistroDatos {
  codigo: string; turno: string; expediente: string; actividad: string; versionPlantilla: number;
  inicioCaptura: string; finCaptura?: string; origen: 'TACTIL' | 'VOZ'; oportunidad: boolean;
  motivoSinOportunidad?: MotivoSinOportunidad; motivoOtro?: string; noQuiso: boolean; nota?: string;
  nivel?: NivelApoyo; pasos: PasoRegistro[]; secuenciaCorrecta?: boolean; camposModificados: string[];
  pendiente: boolean; rechazado?: string;
}

/** Valida que los campos de una captura confirmada respeten las reglas de Registro.java. */
export function validarRegistro(registro: RegistroDatos): void {
  if (!registro.codigo || !registro.turno || !registro.expediente || !registro.actividad) throw new Error('Faltan datos del registro');
  if (!registro.finCaptura || Date.parse(registro.finCaptura) < Date.parse(registro.inicioCaptura)) throw new Error('La captura no puede terminar antes de empezar');
  if (!registro.oportunidad && !registro.motivoSinOportunidad) throw new Error('Sin oportunidad exige un motivo');
  if (registro.oportunidad && registro.motivoSinOportunidad) throw new Error('Con oportunidad no hay motivo');
  if ((registro.motivoSinOportunidad === 'OTRO') !== Boolean(registro.motivoOtro?.trim())) throw new Error('El motivo Otro exige texto');
  if (registro.oportunidad && (registro.motivoOtro || registro.motivoSinOportunidad)) throw new Error('Con oportunidad no hay motivo');
  const requiereNivel = registro.oportunidad && !registro.noQuiso;
  if (requiereNivel !== Boolean(registro.nivel)) throw new Error(requiereNivel ? 'Con oportunidad el nivel de apoyo es obligatorio' : 'Sin oportunidad o con «no quiso» no hay nivel de apoyo');
  const requierePasos = registro.oportunidad && !registro.noQuiso;
  if (requierePasos && !registro.pasos.length) throw new Error('El registro debe incluir todos los pasos');
  if (!requierePasos && registro.pasos.length) throw new Error('Sin oportunidad o con «no quiso» los pasos deben estar vacíos');
  if (registro.pasos.some((paso, indice) => paso.orden !== indice + 1)) throw new Error('El registro debe incluir todos los pasos');
}
