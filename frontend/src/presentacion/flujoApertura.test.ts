import 'fake-indexeddb/auto';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AlmacenLocal, type DatosTurnoLocal, type SesionLocal, type TurnoLocal } from '../infraestructura/almacenLocal';
import { recuperarTurnoAlAbrir } from './flujoApertura';

const sesion: SesionLocal = { token: 'token-sintetico', cuenta: { codigo: 'CUI-01', nombreCompleto: 'Cuidador Uno', rol: 'CUIDADOR' } };
const turno: TurnoLocal = { codigo: 'turno-sintetico', tipo: 'M', declaradoEn: '2026-03-02T08:30:00-05:00', encadenados: [], inicioVentana: '2026-03-02T07:00:00-05:00', finVentana: '2026-03-02T13:00:00-05:00', pendiente: false };
const datos: DatosTurnoLocal = { expedientes: [], actividadesResidente: [], actividades: [], escala: {}, ultimosRegistros: [] };

describe('apertura de turno (SD-01 1.3, 1.3.1; RF-02, DD-56)', () => {
  let almacen: AlmacenLocal;
  beforeEach(() => { almacen = new AlmacenLocal(`apertura-${crypto.randomUUID()}`); });

  it('a: prioriza el turno local vigente sin consultar al servidor', async () => {
    const fecha = new Date('2026-03-02T10:00:00-05:00');
    await almacen.guardarTurno(turno);
    const api = vi.fn();
    expect(await recuperarTurnoAlAbrir(sesion, almacen, true, api, api, fecha)).toEqual(turno);
    expect(api).not.toHaveBeenCalled();
  });

  it('b: recupera el turno del servidor, lo guarda y descarga sus datos', async () => {
    const consultar = vi.fn().mockResolvedValue(turno);
    const descargar = vi.fn().mockResolvedValue(datos);
    expect(await recuperarTurnoAlAbrir(sesion, almacen, true, consultar, descargar, new Date('2026-03-02T10:00:00-05:00'))).toEqual(turno);
    expect(consultar).toHaveBeenCalledOnce();
    expect(descargar).toHaveBeenCalledWith(turno.codigo);
    expect(await almacen.obtenerTurnoVigente(new Date('2026-03-02T10:00:00-05:00'))).toEqual(turno);
    expect(await almacen.obtenerDatosTurno()).toEqual(datos);
  });

  it('c: abre declaración si el servidor no tiene turno o no hay señal', async () => {
    const consultar = vi.fn().mockResolvedValue(undefined);
    const descargar = vi.fn();
    expect(await recuperarTurnoAlAbrir(sesion, almacen, true, consultar, descargar)).toBeUndefined();
    expect(await recuperarTurnoAlAbrir(sesion, almacen, false, consultar, descargar)).toBeUndefined();
    expect(consultar).toHaveBeenCalledOnce();
    expect(descargar).not.toHaveBeenCalled();
  });
});
