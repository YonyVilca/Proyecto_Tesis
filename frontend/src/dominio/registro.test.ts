import { describe, expect, it } from 'vitest';
import { validarRegistro, type RegistroDatos } from './registro';

const base: RegistroDatos = { codigo: 'reg-sintetico', turno: 'turno-sintetico', expediente: 'exp-sintetico', actividad: 'ACT-1', versionPlantilla: 1,
  inicioCaptura: '2026-03-02T09:00:00-05:00', finCaptura: '2026-03-02T09:01:00-05:00', origen: 'TACTIL', oportunidad: true,
  noQuiso: false, nivel: 'NIVEL-1', pasos: [{ orden: 1, cumplido: true }], camposModificados: [], pendiente: true };

describe('RegistroPorPasos (RF-17–RF-21, RF-70, D-41)', () => {
  it('exige nivel con oportunidad y sin negativa', () => { expect(() => validarRegistro({ ...base, nivel: undefined })).toThrow('nivel de apoyo es obligatorio'); });
  it('prohíbe nivel sin oportunidad o con negativa', () => {
    expect(() => validarRegistro({ ...base, oportunidad: false, motivoSinOportunidad: 'NORMA_DEL_HOGAR', nivel: 'NIVEL-1' })).toThrow('no hay nivel');
    expect(() => validarRegistro({ ...base, noQuiso: true, nivel: 'NIVEL-1' })).toThrow('no hay nivel');
  });
  it('exige motivo sin oportunidad y texto para OTRO', () => {
    expect(() => validarRegistro({ ...base, oportunidad: false })).toThrow('exige un motivo');
    expect(() => validarRegistro({ ...base, oportunidad: false, motivoSinOportunidad: 'OTRO' })).toThrow('Otro exige texto');
  });
  it('exige todos los pasos en orden', () => { expect(() => validarRegistro({ ...base, pasos: [{ orden: 2, cumplido: true }] })).toThrow('todos los pasos'); });
});
