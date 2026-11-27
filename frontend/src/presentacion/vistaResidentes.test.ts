// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest';
import type { FundamentoVisible, TarjetaResidenteVisible } from '../infraestructura/api';
import { mostrarVistaResidentes } from './vistaResidentes';

const residentes: TarjetaResidenteVisible[] = [
  { codigo: 'res-1', nombreVisible: 'A. Ficticia', iniciales: 'AF', color: '#aabbcc', actividades: [
    { codigo: 'vestirse', nombre: 'Vestirse', estado: 'SIN_DATOS', negativas: 2 },
  ] },
  { codigo: 'res-2', nombreVisible: 'B. Ficticio', iniciales: 'BF', color: '#bbccdd', actividades: [] },
];
const fundamento: FundamentoVisible = {
  actividad: 'vestirse', nombreActividad: 'Vestirse', regla: 'RB-08 datos suficientes', umbral: 0.6,
  texto: 'cobertura 40%, se necesita 60%', registrosOrigen: [{ fechaHora: '2026-11-26T12:00:00',
    oportunidad: 'SI', motivoSinOportunidad: null, noQuiso: false, nivelApoyo: 'N1', participo: null,
    pasos: [{ orden: 1, cumplido: true }, { orden: 2, cumplido: false }] }],
};

describe('vista de residentes (RF-53, RF-99, RF-100)', () => {
  it('conserva el orden visible del servicio y muestra estado y negativas aparte', () => {
    document.body.innerHTML = '<main></main>';
    mostrarVistaResidentes(document.querySelector('main')!, residentes, vi.fn());
    const tarjetas = Array.from(document.querySelectorAll('.tarjeta-residente'));
    expect(tarjetas.map((tarjeta) => tarjeta.querySelector('h3')?.textContent)).toEqual(['A. Ficticia', 'B. Ficticio']);
    expect(tarjetas[0].textContent).toContain('Sin datos');
    expect(tarjetas[0].textContent).toContain('Se negó: 2 veces');
    expect(tarjetas[1].textContent).toContain('Sin actividades vigentes.');
  });

  it('al tocar actividad consulta y presenta regla, fundamento y registros de origen', async () => {
    document.body.innerHTML = '<main></main>';
    const consultar = vi.fn().mockResolvedValue(fundamento);
    mostrarVistaResidentes(document.querySelector('main')!, residentes.slice(0, 1), consultar);
    const boton = document.querySelector<HTMLButtonElement>('.actividad-estado')!;
    boton.click();
    await vi.waitFor(() => expect(document.querySelector('.fundamento')).not.toBeNull());
    expect(consultar).toHaveBeenCalledWith('res-1', 'vestirse');
    expect(document.querySelector('.fundamento')?.textContent).toContain('cobertura 40%, se necesita 60%');
    expect(document.querySelector('.fundamento')?.textContent).toContain('RB-08 datos suficientes');
    expect(document.querySelector('.fundamento')?.textContent).toContain('1 de 2 pasos cumplidos');
    expect(boton.getAttribute('aria-expanded')).toBe('true');
  });
});
