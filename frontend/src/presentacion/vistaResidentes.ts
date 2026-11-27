import type { FundamentoVisible, TarjetaResidenteVisible } from '../infraestructura/api';
import { MOTIVOS_SIN_OPORTUNIDAD } from './textosRegistro';

const ESTADOS: Record<TarjetaResidenteVisible['actividades'][number]['estado'], string> = {
  SIN_OPORTUNIDAD: 'Sin oportunidad', SIN_DATOS: 'Sin datos', DOMINA: 'Domina',
  RETROCEDE: 'Retrocede', ESTANCADA: 'Estancada', EN_TRABAJO: 'En trabajo',
};

/** Renderiza las tarjetas y el fundamento seleccionado (RF-53, RF-99, RF-100, CU-05). */
export function mostrarVistaResidentes(
  contenedor: HTMLElement,
  residentes: TarjetaResidenteVisible[],
  consultarFundamento: (residente: string, actividad: string) => Promise<FundamentoVisible>,
): void {
  const seccion = document.createElement('section');
  seccion.className = 'tablero-residentes';
  seccion.append(elemento('h2', 'Residentes'));
  if (residentes.length === 0) seccion.append(elemento('p', 'No hay residentes activos.'));

  // El servicio entrega el orden por nombre visible; la presentación lo conserva.
  for (const residente of residentes) {
    const tarjeta = document.createElement('article');
    tarjeta.className = 'tarjeta-residente';
    const cabecera = document.createElement('div');
    cabecera.className = 'identidad-residente';
    const iniciales = elemento('span', residente.iniciales, 'iniciales');
    iniciales.style.backgroundColor = residente.color;
    iniciales.setAttribute('aria-label', `Color ${residente.color}`);
    cabecera.append(iniciales, elemento('h3', residente.nombreVisible));
    tarjeta.append(cabecera);
    if (residente.actividades.length === 0) tarjeta.append(elemento('p', 'Sin actividades vigentes.', 'sin-actividades'));

    for (const actividad of residente.actividades) {
      const bloque = document.createElement('section');
      bloque.className = 'actividad-residente';
      const boton = document.createElement('button');
      boton.type = 'button'; boton.className = 'actividad-estado';
      boton.setAttribute('aria-expanded', 'false');
      boton.setAttribute('aria-label', `${actividad.nombre}: ${ESTADOS[actividad.estado]}. Ver fundamento`);
      boton.append(elemento('span', actividad.nombre), elemento('span', ESTADOS[actividad.estado], `estado estado-${actividad.estado.toLowerCase()}`));
      boton.addEventListener('click', () => {
        const previo = bloque.querySelector('.fundamento');
        if (previo) { previo.remove(); boton.setAttribute('aria-expanded', 'false'); return; }
        boton.disabled = true;
        void consultarFundamento(residente.codigo, actividad.codigo).then((fundamento) => {
          if (!bloque.isConnected) return;
          bloque.append(renderizarFundamento(fundamento));
          boton.setAttribute('aria-expanded', 'true');
        }).catch(() => {
          if (bloque.isConnected) bloque.append(elemento('p', 'No se pudo cargar el fundamento.', 'error'));
        }).finally(() => { boton.disabled = false; });
      });
      bloque.append(boton);
      if (actividad.negativas > 0) bloque.append(elemento('p', `Se negó: ${actividad.negativas} ${actividad.negativas === 1 ? 'vez' : 'veces'} en los últimos 7 días.`, 'negativas'));
      tarjeta.append(bloque);
    }
    seccion.append(tarjeta);
  }
  contenedor.append(seccion);
}

function renderizarFundamento(fundamento: FundamentoVisible): HTMLElement {
  const detalle = document.createElement('div'); detalle.className = 'fundamento';
  detalle.append(elemento('p', fundamento.texto, 'texto-fundamento'));
  detalle.append(elemento('p', `Regla: ${fundamento.regla}${fundamento.umbral === null ? '' : ` · Umbral: ${fundamento.umbral}`}`, 'regla-fundamento'));
  const lista = document.createElement('ul'); lista.className = 'registros-origen';
  for (const registro of fundamento.registrosOrigen) {
    const fecha = new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Lima' }).format(new Date(registro.fechaHora));
    const textoMotivo = registro.motivoSinOportunidad
      ? MOTIVOS_SIN_OPORTUNIDAD.find(([codigo]) => codigo === registro.motivoSinOportunidad)?.[1] ?? 'Otro'
      : '';
    const motivos: string[] = [registro.oportunidad === 'NO' ? `Sin oportunidad${textoMotivo ? ` (${textoMotivo})` : ''}` : 'Con oportunidad'];
    if (registro.noQuiso) motivos.push('No quiso');
    if (registro.nivelApoyo) motivos.push(registro.nivelApoyo);
    if (registro.participo !== null) motivos.push(registro.participo ? 'Participó' : 'No participó');
    if (registro.pasos.length) motivos.push(`${registro.pasos.filter((paso) => paso.cumplido).length} de ${registro.pasos.length} pasos cumplidos`);
    lista.append(elemento('li', `${fecha} · ${motivos.join(' · ')}`));
  }
  if (fundamento.registrosOrigen.length === 0) lista.append(elemento('li', 'No hay registros de origen.'));
  detalle.append(elemento('h4', 'Registros considerados'), lista);
  return detalle;
}

function elemento<K extends keyof HTMLElementTagNameMap>(tag: K, texto: string, clase = ''): HTMLElementTagNameMap[K] {
  const nodo = document.createElement(tag); nodo.textContent = texto; if (clase) nodo.className = clase; return nodo;
}
