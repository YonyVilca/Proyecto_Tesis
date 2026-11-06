import { describe, expect, it } from 'vitest';
import { ETIQUETAS_REGISTRO, MOTIVOS_SIN_OPORTUNIDAD } from './textosRegistro';

describe('Textos de captura (RF-70)', () => {
  it('expone etiquetas visibles para todos los controles', () => {
    expect(Object.values(ETIQUETAS_REGISTRO)).toEqual([
      'Oportunidad', 'Motivo', 'Podía hacerlo y no quiso', 'Nivel de apoyo', 'Pasos', 'Secuencia', 'Nota', 'Otro',
    ]);
  });
  it('presenta los motivos con texto humano', () => {
    expect(MOTIVOS_SIN_OPORTUNIDAD.map(([, texto]) => texto)).toEqual([
      'Norma del hogar', 'Faltan materiales o recursos', 'El cuidador acompañaba a otro residente',
      'No estaba en el hogar', 'Enfermo o indispuesto', 'Otro',
    ]);
  });
});
