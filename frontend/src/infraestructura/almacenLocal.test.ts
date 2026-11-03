import 'fake-indexeddb/auto';
import { beforeEach, describe, expect, it } from 'vitest';
import { AlmacenLocal, type DatosTurnoLocal, type TurnoLocal } from './almacenLocal';
import type { RegistroDatos } from '../dominio/registro';

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

  it('guarda registros pendientes y los suma a la precarga sin nivel de apoyo', async () => {
    await almacen.guardarDatosTurno(datos);
    const registro: RegistroDatos = { codigo: 'registro-sintetico', turno: turno.codigo, expediente: 'expediente-sintetico', actividad: 'ACT-1', versionPlantilla: 2,
      inicioCaptura: '2026-03-02T09:00:00-05:00', finCaptura: '2026-03-02T09:01:00-05:00', origen: 'TACTIL', oportunidad: true,
      noQuiso: false, nivel: 'NIVEL-3', pasos: [{ orden: 1, cumplido: true }], camposModificados: [], pendiente: true };
    await almacen.guardarRegistro(registro);
    expect(await almacen.obtenerPendientes()).toEqual([registro]);
    expect(await almacen.obtenerDatosTurno()).toMatchObject({ ultimosRegistros: [
      { codigo: registro.codigo, expediente: registro.expediente, actividad: registro.actividad, versionPlantilla: 2,
        pasos: [{ orden: 1, cumplido: true }] },
    ] });
    expect(JSON.stringify(await almacen.obtenerDatosTurno())).not.toContain('NIVEL-3');
    await almacen.marcarRegistroEnviado(registro.codigo);
    expect(await almacen.obtenerPendientes()).toHaveLength(0);
  });
});
