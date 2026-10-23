import 'fake-indexeddb/auto';
import { beforeEach, describe, expect, it } from 'vitest';
import { AlmacenLocal, type DatosTurnoLocal, type TurnoLocal } from './almacenLocal';

const turno: TurnoLocal = {
  codigo: 'turno-sintetico', tipo: 'M', declaradoEn: '2026-03-02T08:30:00-05:00', encadenados: [],
  inicioVentana: '2026-03-02T07:00:00-05:00', finVentana: '2026-03-02T13:00:00-05:00', pendiente: false,
};
const datos: DatosTurnoLocal = {
  expedientes: [{ codigo: 'expediente-sintetico', nombreVisible: 'Residente A', iniciales: 'RA', color: '#123456' }],
  actividadesResidente: [], actividades: [], escala: {}, ultimosRegistros: [],
};

describe('AlmacenLocal (DC-06, RF-02, RF-101, D-67)', () => {
  let almacen: AlmacenLocal;
  let numeroDb = 0;
  beforeEach(() => {
    almacen = new AlmacenLocal(`hogar-local-test-${numeroDb++}`);
  });

  it('guarda, obtiene y borra la sesión', async () => {
    const sesion = { token: 'token-sintetico', cuenta: { codigo: 'CUI-01', nombreCompleto: 'Cuidador Uno', rol: 'CUIDADOR' } };
    await almacen.guardarSesion(sesion);
    expect(await almacen.obtenerSesion()).toEqual(sesion);
    await almacen.borrarSesion();
    expect(await almacen.obtenerSesion()).toBeUndefined();
  });

  it('guarda el turno y devuelve el vigente en el instante indicado', async () => {
    await almacen.guardarTurno(turno);
    expect(await almacen.obtenerTurnoVigente(new Date('2026-03-02T10:00:00-05:00'))).toEqual(turno);
    expect(await almacen.obtenerTurnoVigente(new Date('2026-03-02T13:01:00-05:00'))).toBeUndefined();
  });

  it('marca el turno como pendiente sin alterar los demás campos', async () => {
    await almacen.guardarTurno(turno);
    await almacen.marcarTurnoPendiente(turno.codigo);
    expect(await almacen.obtenerTurnoVigente(new Date('2026-03-02T10:00:00-05:00'))).toMatchObject({ ...turno, pendiente: true });
  });

  it('guarda y recupera la descarga operativa', async () => {
    await almacen.guardarDatosTurno(datos);
    expect(await almacen.obtenerDatosTurno()).toEqual(datos);
  });
});
