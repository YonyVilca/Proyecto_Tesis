import { describe, expect, it } from 'vitest';
import { resumenEnvio } from './resumenEnvio';

describe('resumenEnvio (HU-23, RF-31)', () => {
  it('informa los registros pendientes', () => {
    expect(resumenEnvio(2, 0)).toBe('2 registro(s) pendientes de envío. Se enviarán cuando haya señal.');
  });
  it('prioriza los rechazos para que se puedan consultar', () => {
    expect(resumenEnvio(1, 3)).toBe('3 registro(s) no aceptados por el servidor');
  });
  it('indica que se envió todo', () => {
    expect(resumenEnvio(0, 0)).toBe('Todo enviado.');
  });
});
