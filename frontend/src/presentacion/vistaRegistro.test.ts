// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mostrarVistaRegistro } from './vistaRegistro';
import type { DatosTurnoLocal, IAlmacenLocal, TurnoLocal } from '../infraestructura/almacenLocal';

const residente = { codigo: 'exp-sintetico-1', nombreVisible: 'Residente Ficticio', iniciales: 'RF', color: '#aabbcc' };
const actividad = { codigo: 'act-1', nombre: 'Vestirse', modalidad: 'PASOS', secuenciaRelevante: true,
  plantilla: { version: 1, pasos: [{ orden: 1, descripcion: 'Elegir ropa' }] } };
const datos: DatosTurnoLocal = {
  expedientes: [residente], actividadesResidente: [{ expediente: residente.codigo, actividad: actividad.codigo, estado: 'ASIGNADA' }],
  actividades: [actividad], escala: { niveles: [{ codigo: 'N1', nombre: 'Apoyo leve' }] }, ultimosRegistros: [],
};
const turno: TurnoLocal = { codigo: 'turno-1', tipo: 'M', declaradoEn: new Date().toISOString(), encadenados: [],
  inicioVentana: new Date(Date.now() - 60_000).toISOString(), finVentana: new Date(Date.now() + 60_000).toISOString(), pendiente: false };

function preparar(datosTurno = datos) {
  document.body.innerHTML = '<main id="app"></main>';
  const guardarRegistro = vi.fn().mockResolvedValue(undefined);
  const almacen = { guardarRegistro } as unknown as IAlmacenLocal;
  const app = document.querySelector<HTMLElement>('#app')!;
  mostrarVistaRegistro(app, turno, datosTurno, almacen, vi.fn().mockResolvedValue(undefined), vi.fn());
  return { app, guardarRegistro };
}

function campo(nombre: string): HTMLSelectElement {
  const etiqueta = Array.from(document.querySelectorAll('label')).find((label) => label.querySelector('span')?.textContent?.startsWith(nombre));
  return etiqueta!.querySelector('select')!;
}

function comenzar(app: HTMLElement): void {
  (Array.from(app.querySelectorAll('button')).find((boton) => boton.textContent === 'Comenzar') as HTMLButtonElement).click();
  (app.querySelector('fieldset input[type=checkbox]') as HTMLInputElement).click();
  (Array.from(app.querySelectorAll('button')).find((boton) => boton.textContent === 'Comenzar') as HTMLButtonElement).click();
}

function confirmar(app: HTMLElement): void {
  app.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
}

describe('vista de registro (AD-06, CU-01, RF-18)', () => {
  beforeEach(() => { vi.restoreAllMocks(); });

  it('guarda un pendiente con oportunidad y nivel y confirma el guardado local', async () => {
    const { app, guardarRegistro } = preparar(); comenzar(app);
    campo('Nivel de apoyo').value = 'N1';
    confirmar(app);
    await vi.waitFor(() => expect(guardarRegistro).toHaveBeenCalledOnce());
    expect(guardarRegistro.mock.calls[0][0]).toMatchObject({ pendiente: true, oportunidad: true, nivel: 'N1' });
    expect(app.textContent).toContain('Registro guardado en el teléfono');
  });

  it('muestra el error junto a Confirmar, marca Nivel y no guarda si falta el nivel', () => {
    const { app, guardarRegistro } = preparar(); comenzar(app);
    const scroll = vi.fn(); Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', { configurable: true, value: scroll });
    confirmar(app);
    const confirmarBtn = Array.from(app.querySelectorAll('button')).find((boton) => boton.textContent === 'Confirmar')!;
    const alerta = confirmarBtn.parentElement!.querySelector('[role="alert"]')!;
    expect(alerta.textContent).toContain('nivel de apoyo es obligatorio');
    expect(alerta.nextElementSibling).toBe(confirmarBtn);
    expect((app.querySelector('[aria-invalid="true"]') as HTMLSelectElement).value).toBe('');
    expect(scroll).toHaveBeenCalledOnce();
    expect(guardarRegistro).not.toHaveBeenCalled();
  });

  it('guarda sin oportunidad con un motivo y pasos vacíos', async () => {
    const { app, guardarRegistro } = preparar(); comenzar(app);
    campo('Oportunidad').value = 'NO'; campo('Motivo').value = 'NO_ESTABA_EN_EL_HOGAR';
    confirmar(app);
    await vi.waitFor(() => expect(guardarRegistro).toHaveBeenCalledOnce());
    expect(guardarRegistro.mock.calls[0][0]).toMatchObject({ oportunidad: false, motivoSinOportunidad: 'NO_ESTABA_EN_EL_HOGAR', pasos: [] });
  });

  it('guarda con «no quiso» y pasos vacíos', async () => {
    const { app, guardarRegistro } = preparar(); comenzar(app);
    (Array.from(app.querySelectorAll('label')).find((label) => label.textContent?.includes('Podía hacerlo y no quiso'))!.querySelector('input')!).click();
    confirmar(app);
    await vi.waitFor(() => expect(guardarRegistro).toHaveBeenCalledOnce());
    expect(guardarRegistro.mock.calls[0][0]).toMatchObject({ oportunidad: true, noQuiso: true, pasos: [] });
  });

  it('avisa al comenzar sin residentes marcados o sin residentes asignados', () => {
    const { app } = preparar();
    (Array.from(app.querySelectorAll('button')).find((boton) => boton.textContent === 'Comenzar') as HTMLButtonElement).click();
    expect(app.textContent).toContain('Elige al menos un residente');
    const sinAsignacion = { ...datos, actividadesResidente: [] };
    preparar(sinAsignacion).app.querySelector('button')!.dispatchEvent(new Event('click'));
    expect(document.querySelector('#app')!.textContent).toContain('No hay residentes para esta actividad');
  });

  it('guarda una sola vez ante dos envíos seguidos', async () => {
    const { app, guardarRegistro } = preparar(); comenzar(app); campo('Nivel de apoyo').value = 'N1';
    confirmar(app); confirmar(app);
    await vi.waitFor(() => expect(guardarRegistro).toHaveBeenCalledOnce());
  });
});
