import type { MotivoSinOportunidad } from '../dominio/registro';

/** Etiquetas visibles de captura (AD-06, RF-70). */
export const ETIQUETAS_REGISTRO = {
  oportunidad: 'Oportunidad', motivo: 'Motivo', negativa: 'Podía hacerlo y no quiso',
  nivel: 'Nivel de apoyo', pasos: 'Pasos', secuencia: 'Secuencia', nota: 'Nota', otro: 'Otro',
} as const;

/** Motivos de falta de oportunidad en lenguaje cotidiano (RF-70). */
export const MOTIVOS_SIN_OPORTUNIDAD: Array<[MotivoSinOportunidad, string]> = [
  ['NORMA_DEL_HOGAR', 'Norma del hogar'],
  ['FALTAN_RECURSOS', 'Faltan materiales o recursos'],
  ['CUIDADOR_CON_OTRO_RESIDENTE', 'El cuidador acompañaba a otro residente'],
  ['NO_ESTABA_EN_EL_HOGAR', 'No estaba en el hogar'],
  ['ENFERMO_O_INDISPUESTO', 'Enfermo o indispuesto'],
  ['OTRO', 'Otro'],
];
