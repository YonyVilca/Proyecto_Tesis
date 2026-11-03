import 'fake-indexeddb/auto';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AlmacenLocal, type TurnoLocal } from '../infraestructura/almacenLocal';
import type { RegistroDatos } from '../dominio/registro';
import { MotorEnvio } from './motorEnvio';

const turno: TurnoLocal = { codigo: 'turno-sintetico', tipo: 'M', declaradoEn: '2026-03-02T08:30:00-05:00', encadenados: [],
  inicioVentana: '2026-03-02T07:00:00-05:00', finVentana: '2026-03-02T13:00:00-05:00', pendiente: true };

describe('MotorEnvio (AD-05, RF-29, RF-32)', () => {
  let almacen: AlmacenLocal;
  let numeroDb = 0;
  beforeEach(async () => { almacen = new AlmacenLocal(`motor-test-${numeroDb++}`); await almacen.guardarTurno(turno); });

  it('conserva el turno pendiente cuando no hay red y lo reenvía una sola vez al volver', async () => {
    const enviarTurno = vi.fn().mockRejectedValueOnce(new Error('sin red')).mockResolvedValueOnce('ACEPTADO');
    const datos = { expedientes: [], actividadesResidente: [], actividades: [], escala: {}, ultimosRegistros: [] };
    const obtenerDatos = vi.fn().mockResolvedValue(datos);
    const motor = new MotorEnvio(almacen, { enviarTurno, obtenerDatos });
    await motor.enviarPendientes();
    expect(await almacen.obtenerTurnosPendientes()).toHaveLength(1);
    await Promise.all([motor.enviarPendientes(), motor.enviarPendientes()]);
    expect(enviarTurno).toHaveBeenCalledTimes(2);
    expect(obtenerDatos).toHaveBeenCalledOnce();
    expect(await almacen.obtenerTurnosPendientes()).toHaveLength(0);
    expect(await almacen.obtenerDatosTurno()).toEqual(datos);
  });

  it('espera al turno y marca enviado un reintento DUPLICADO', async () => {
    const registro: RegistroDatos = { codigo: 'registro-sintetico', turno: turno.codigo, expediente: 'exp-sintetico', actividad: 'ACT-1', versionPlantilla: 1,
      inicioCaptura: '2026-03-02T09:00:00-05:00', finCaptura: '2026-03-02T09:01:00-05:00', origen: 'TACTIL', oportunidad: true,
      noQuiso: false, nivel: 'NIVEL-1', pasos: [{ orden: 1, cumplido: true }], camposModificados: [], pendiente: true };
    await almacen.guardarRegistro(registro);
    const enviarRegistro = vi.fn().mockResolvedValue('DUPLICADO');
    const enviarTurno = vi.fn().mockRejectedValueOnce(new Error('sin red')).mockResolvedValueOnce('ACEPTADO');
    const motor = new MotorEnvio(almacen, { enviarTurno,
      obtenerDatos: vi.fn().mockResolvedValue({ expedientes: [], actividadesResidente: [], actividades: [], escala: {}, ultimosRegistros: [] }), enviarRegistro });
    await motor.enviarPendientes();
    expect(enviarRegistro).not.toHaveBeenCalled();
    expect(await almacen.obtenerPendientes()).toHaveLength(1);
    await motor.enviarPendientes();
    expect(enviarRegistro).toHaveBeenCalledOnce();
    expect(await almacen.obtenerPendientes()).toHaveLength(0);
  });

  it('conserva el registro ante un corte de red y acepta DUPLICADO al reintentar', async () => {
    const registro: RegistroDatos = { codigo: 'registro-sintetico-2', turno: turno.codigo, expediente: 'exp-sintetico', actividad: 'ACT-1', versionPlantilla: 1,
      inicioCaptura: '2026-03-02T09:00:00-05:00', finCaptura: '2026-03-02T09:01:00-05:00', origen: 'TACTIL', oportunidad: true,
      noQuiso: false, nivel: 'NIVEL-1', pasos: [{ orden: 1, cumplido: true }], camposModificados: [], pendiente: true };
    await almacen.marcarTurnoEnviado(turno.codigo);
    await almacen.guardarRegistro(registro);
    const enviarRegistro = vi.fn().mockRejectedValueOnce(new Error('corte de red')).mockResolvedValueOnce('DUPLICADO');
    const motor = new MotorEnvio(almacen, { enviarTurno: vi.fn(), obtenerDatos: vi.fn(), enviarRegistro });
    await motor.enviarPendientes();
    expect(await almacen.obtenerPendientes()).toHaveLength(1);
    await motor.enviarPendientes();
    expect(enviarRegistro).toHaveBeenCalledTimes(2);
    expect(await almacen.obtenerPendientes()).toHaveLength(0);
  });
});
