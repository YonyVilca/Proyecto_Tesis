import { describe, expect, it } from 'vitest';
import { TurnoDeclarado, type TipoTurno } from './turno';

const instanteLima = (hora: number, minuto = 0, dia = 2) => new Date(Date.UTC(2026, 2, dia, hora + 5, minuto));
const turno = (tipo: TipoTurno, hora: number, minuto = 0) => new TurnoDeclarado('turno-sintetico', tipo, instanteLima(hora, minuto));
const marcaLocal = (hora: number, minuto = 0, dia = 2) => new Date(Date.UTC(2026, 2, dia, hora + 5, minuto));

describe('TurnoDeclarado (RF-12, RF-90, DD-27)', () => {
  it('sugiere el tipo según la hora de Lima', () => {
    expect(TurnoDeclarado.sugerirTipo(instanteLima(7))).toBe('M');
    expect(TurnoDeclarado.sugerirTipo(instanteLima(13))).toBe('T');
    expect(TurnoDeclarado.sugerirTipo(instanteLima(19))).toBe('N');
    expect(TurnoDeclarado.sugerirTipo(instanteLima(6, 59))).toBe('N');
  });

  it('sitúa el inicio de la noche declarada de madrugada en el día anterior', () => {
    const n = turno('N', 2, 30);
    expect(n.inicioVentana).toEqual(marcaLocal(19, 0, 1));
    expect(n.finVentana).toEqual(marcaLocal(7, 0, 2));
  });

  it('aplica tolerancia del servidor sin ampliarla en el teléfono', () => {
    const m = turno('M', 7, 5);
    expect(m.contieneMomento(instanteLima(13, 10))).toBe(false);
    expect(m.contieneMomento(instanteLima(13, 10), 15)).toBe(true);
    expect(m.contieneMomento(instanteLima(13, 16), 15)).toBe(false);
  });

  it('no habilita el registro durante descanso', () => {
    const d = turno('D', 9);
    expect(d.habilitaRegistro()).toBe(false);
    expect(d.contieneMomento(instanteLima(9), 15)).toBe(false);
  });

  it('encadena un turno contiguo y amplía la ventana', () => {
    const m = turno('M', 7, 5);
    m.encadenar('T');
    expect(m.finVentana).toEqual(marcaLocal(19));
    expect(m.encadenados).toEqual(['T']);
    expect(m.contieneMomento(instanteLima(18))).toBe(true);
  });

  it('rechaza encadenar un turno no contiguo', () => {
    expect(() => turno('M', 7, 5).encadenar('N')).toThrow(RangeError);
  });
});
