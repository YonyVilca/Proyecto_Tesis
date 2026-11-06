import { construirRegistro, residentesPara } from '../aplicacion/captura';
import { validarRegistro, type RegistroDatos } from '../dominio/registro';
import type { DatosTurnoLocal, TurnoLocal, IAlmacenLocal } from '../infraestructura/almacenLocal';
import { ETIQUETAS_REGISTRO, MOTIVOS_SIN_OPORTUNIDAD } from './textosRegistro';

interface Actividad { codigo: string; nombre: string; modalidad: string; secuenciaRelevante: boolean; plantilla: { version: number; pasos: Array<{ orden: number; descripcion: string }> } }
interface Resumen { codigo?: string; expediente: string; actividad: string; versionPlantilla: number; pasos: Array<{ orden: number; cumplido: boolean }>; secuenciaCorrecta?: boolean; oportunidad?: boolean }
/** Presenta captura secuencial por residente conforme a AD-06, CU-01 y SD-03 (RF-18, RF-31, RF-70, RF-98). */
export function mostrarVistaRegistro(app: HTMLElement, turno: TurnoLocal, datos: DatosTurnoLocal, almacen: IAlmacenLocal,
  procesarPendientes: () => Promise<void>, alTerminar: () => void | Promise<void>): void {
  const actividades = (datos.actividades as Actividad[]).filter((a) => a.modalidad === 'PASOS' && residentesPara(datos, a.codigo).length > 0)
    .sort((a, b) => prioridad(datos, b.codigo) - prioridad(datos, a.codigo));
  app.replaceChildren();
  const titulo = el('h1', 'Registrar actividad');
  const actividad = el('select');
  actividades.forEach((a) => { const opcion = el('option', a.nombre); opcion.value = a.codigo; actividad.append(opcion); });
  app.append(titulo, conEtiqueta('Actividad', actividad));

  const seleccion = el('fieldset'); seleccion.append(el('legend', 'Residentes'));
  app.append(seleccion);
  const cargarResidentes = () => {
    seleccion.replaceChildren(el('legend', 'Residentes'));
    residentesPara(datos, actividad.value).forEach((residente) => {
      const check = el('input'); check.type = 'checkbox'; check.value = residente.codigo;
      const etiqueta = el('label'); etiqueta.append(check, document.createTextNode(residente.nombreVisible)); seleccion.append(etiqueta);
    });
  };
  actividad.addEventListener('change', cargarResidentes);
  cargarResidentes();
  const comenzar = el('button', 'Comenzar'); comenzar.type = 'button'; app.append(comenzar);
  comenzar.onclick = () => {
    const seleccionados = Array.from(seleccion.querySelectorAll<HTMLInputElement>('input:checked')).map((check) => check.value);
    if (!seleccionados.length || !actividades.length) return;
    const actividadElegida = actividades.find((a) => a.codigo === actividad.value);
    if (!actividadElegida) return;
    let indice = 0;
    const siguiente = () => {
      if (indice >= seleccionados.length) { void alTerminar(); return; }
      const expediente = seleccionados[indice];
      const residente = datos.expedientes.find((item) => item.codigo === expediente);
      if (!residente) { indice++; siguiente(); return; }
      const anterior = (datos.ultimosRegistros as Resumen[]).find((r) => r.expediente === expediente
        && r.actividad === actividadElegida.codigo && r.versionPlantilla === actividadElegida.plantilla.version);
      app.replaceChildren();
      app.append(el('h1', `Residente ${indice + 1} de ${seleccionados.length}: ${residente.nombreVisible}`));
      const inicio = new Date();
      const formulario = el('form'); app.append(formulario);

      const oportunidad = el('select');
      for (const [valor, texto] of [['SI', 'Sí'], ['NO', 'No']] as const) { const o = el('option', texto); o.value = valor; oportunidad.append(o); }
      formulario.append(conEtiqueta(ETIQUETAS_REGISTRO.oportunidad, oportunidad));

      const motivo = el('select');
      MOTIVOS_SIN_OPORTUNIDAD.forEach(([valor, texto]) => { const o = el('option', texto); o.value = valor; motivo.append(o); });
      const otro = el('input'); otro.placeholder = 'Describe el motivo';
      const campoMotivo = conEtiqueta(ETIQUETAS_REGISTRO.motivo, motivo);
      const campoOtro = conEtiqueta(ETIQUETAS_REGISTRO.otro, otro);
      formulario.append(campoMotivo, campoOtro);

      const noQuiso = el('input'); noQuiso.type = 'checkbox';
      formulario.append(conEtiqueta(ETIQUETAS_REGISTRO.negativa, noQuiso));

      const nivel = el('select');
      const vacio = el('option', 'Elige un nivel de apoyo'); vacio.value = ''; nivel.append(vacio);
      ((datos.escala as { niveles?: Array<{ codigo: string; nombre: string }> }).niveles ?? []).forEach((item) => {
        const o = el('option', item.nombre); o.value = item.codigo; nivel.append(o);
      });
      formulario.append(conEtiqueta(ETIQUETAS_REGISTRO.nivel, nivel));

      const grupoPasos = el('fieldset'); grupoPasos.append(el('legend', ETIQUETAS_REGISTRO.pasos));
      const pasos = actividadElegida.plantilla.pasos.map((paso) => {
        const check = el('input'); check.type = 'checkbox';
        check.checked = anterior?.pasos.find((p) => p.orden === paso.orden)?.cumplido ?? false;
        const etiqueta = el('label'); etiqueta.append(check, document.createTextNode(paso.descripcion)); grupoPasos.append(etiqueta); return check;
      });
      formulario.append(grupoPasos);

      const secuencia = el('select');
      for (const [valor, texto] of [['true', 'Correcta'], ['false', 'Incorrecta']] as const) { const o = el('option', texto); o.value = valor; secuencia.append(o); }
      secuencia.value = String(anterior?.secuenciaCorrecta ?? true);
      const campoSecuencia = conEtiqueta(ETIQUETAS_REGISTRO.secuencia, secuencia); formulario.append(campoSecuencia);
      const nota = el('input'); nota.placeholder = 'Nota opcional'; formulario.append(conEtiqueta(ETIQUETAS_REGISTRO.nota, nota));

      const sincronizar = () => {
        const sinOportunidad = oportunidad.value === 'NO';
        campoMotivo.hidden = !sinOportunidad; campoOtro.hidden = !sinOportunidad || motivo.value !== 'OTRO';
        noQuiso.disabled = sinOportunidad;
        nivel.hidden = sinOportunidad || noQuiso.checked;
        pasos.forEach((paso) => { paso.disabled = sinOportunidad || noQuiso.checked; });
        campoSecuencia.hidden = !actividadElegida.secuenciaRelevante || sinOportunidad || noQuiso.checked;
        if (!sinOportunidad) otro.value = '';
      };
      oportunidad.addEventListener('change', sincronizar);
      motivo.addEventListener('change', sincronizar);
      noQuiso.addEventListener('change', sincronizar);
      sincronizar();

      const confirmar = el('button', 'Confirmar'); confirmar.type = 'submit';
      const cancelar = el('button', 'Cancelar'); cancelar.type = 'button';
      formulario.append(confirmar, cancelar);
      const aviso = el('p'); aviso.setAttribute('role', 'status');
      const avanzar = () => { indice++; siguiente(); };
      cancelar.onclick = avanzar;
      formulario.onsubmit = (evento) => {
        evento.preventDefault();
        if (confirmar.disabled) return;
        confirmar.disabled = true;
        try {
          const registro = construirRegistro({
            codigo: crypto.randomUUID(), expediente, actividad: actividadElegida.codigo,
            versionPlantilla: actividadElegida.plantilla.version,
            pasos: actividadElegida.plantilla.pasos.map((paso, i) => ({ orden: paso.orden, cumplido: pasos[i].checked })),
            oportunidad: oportunidad.value === 'SI',
            motivoSinOportunidad: oportunidad.value === 'NO' ? motivo.value as RegistroDatos['motivoSinOportunidad'] : undefined,
            motivoOtro: otro.value, noQuiso: noQuiso.checked, nota: nota.value, nivel: nivel.value,
            secuenciaCorrecta: secuencia.value === 'true', secuenciaRelevante: actividadElegida.secuenciaRelevante,
          }, anterior, turno, inicio, new Date());
          validarRegistro(registro);
          void almacen.guardarRegistro(registro).then(() => {
            app.replaceChildren(el('p', 'Registro guardado en el teléfono'));
            void procesarPendientes();
            window.setTimeout(avanzar, 700);
          }).catch((error: unknown) => {
            aviso.textContent = error instanceof Error ? error.message : 'No se pudo guardar el registro.';
            if (!aviso.isConnected) formulario.prepend(aviso);
            confirmar.disabled = false;
          });
        } catch (error) {
          aviso.textContent = error instanceof Error ? error.message : 'No se pudo guardar el registro.';
          if (!aviso.isConnected) formulario.prepend(aviso);
          confirmar.disabled = false;
        }
      };
    };
    siguiente();
  };
}

function prioridad(datos: DatosTurnoLocal, codigo: string): number {
  return datos.actividadesResidente.some((item) => item.actividad === codigo && item.estado === 'PRIORITARIA') ? 1 : 0;
}

function conEtiqueta(texto: string, control: HTMLElement): HTMLLabelElement {
  const etiqueta = el('label'); etiqueta.append(el('span', texto), control); return etiqueta;
}

function el<K extends keyof HTMLElementTagNameMap>(tag: K, texto = ''): HTMLElementTagNameMap[K] {
  const elemento = document.createElement(tag); if (texto) elemento.textContent = texto; return elemento;
}
