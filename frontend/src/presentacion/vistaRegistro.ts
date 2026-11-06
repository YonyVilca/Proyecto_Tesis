import { construirRegistro } from '../aplicacion/captura';
import { validarRegistro, type RegistroDatos } from '../dominio/registro';
import { TurnoDeclarado } from '../dominio/turno';
import type { DatosTurnoLocal, TurnoLocal, IAlmacenLocal } from '../infraestructura/almacenLocal';

interface Actividad { codigo: string; nombre: string; modalidad: string; secuenciaRelevante: boolean; plantilla: { version: number; pasos: Array<{ orden: number; descripcion: string }> } }
interface Resumen { codigo?: string; expediente: string; actividad: string; versionPlantilla: number; pasos: Array<{ orden: number; cumplido: boolean }>; secuenciaCorrecta?: boolean; oportunidad?: boolean }

/** Presenta captura secuencial por residente conforme a AD-06, CU-01 y SD-03 (RF-18, RF-32, RF-98). */
export function mostrarVistaRegistro(app: HTMLElement, turno: TurnoLocal, datos: DatosTurnoLocal, almacen: IAlmacenLocal,
  procesarPendientes: () => Promise<void>, alTerminar: () => void): void {
  const actividades = datos.actividades as Actividad[];
  const hoy = new Set(datos.actividadesResidente.filter((x) => x.estado !== 'NO_CORRESPONDE').map((x) => x.actividad));
  const opciones = actividades.filter((a) => a.modalidad === 'PASOS' && hoy.has(a.codigo))
    .sort((a, b) => prioridad(datos, b.codigo) - prioridad(datos, a.codigo));
  app.replaceChildren(); const h = document.createElement('h1'); h.textContent = 'Registrar actividad'; app.append(h);
  const actividadSelect = document.createElement('select');
  opciones.forEach((a) => { const o = document.createElement('option'); o.value = a.codigo; o.textContent = a.nombre; actividadSelect.append(o); });
  const etiqueta = document.createElement('label'); etiqueta.append('Actividad ', actividadSelect); app.append(etiqueta);
  const lista = document.createElement('fieldset'); const leyenda = document.createElement('legend'); leyenda.textContent = 'Residentes'; lista.append(leyenda);
  const elegibles = datos.expedientes.filter((r) => datos.actividadesResidente.some((ar) => ar.expediente === r.codigo && ar.estado !== 'NO_CORRESPONDE' && opciones.some((a) => a.codigo === ar.actividad)));
  elegibles.forEach((r) => { const l = document.createElement('label'); const c = document.createElement('input'); c.type = 'checkbox'; c.value = r.codigo; l.append(c, r.nombreVisible); lista.append(l); }); app.append(lista);
  const comenzar = document.createElement('button'); comenzar.textContent = 'Comenzar'; app.append(comenzar);
  comenzar.onclick = () => {
    const seleccionados = Array.from(lista.querySelectorAll<HTMLInputElement>('input:checked')).map((c) => c.value);
    if (!seleccionados.length || !opciones.length) return;
    let indice = 0;
    const siguiente = () => {
      if (indice >= seleccionados.length) { alTerminar(); return; }
      const expediente = seleccionados[indice]; const residente = datos.expedientes.find((r) => r.codigo === expediente)!;
      const actividad = opciones.find((a) => a.codigo === actividadSelect.value)!;
      const previous = datos.ultimosRegistros as Resumen[];
      const anterior = previous.find((r) => r.expediente === expediente && r.actividad === actividad.codigo && r.versionPlantilla === actividad.plantilla.version);
      const inicio = new Date(); app.replaceChildren();
      const titulo = document.createElement('h1'); titulo.textContent = `Residente ${indice + 1} de ${seleccionados.length}: ${residente.nombreVisible}`; app.append(titulo);
      const form = document.createElement('form'); app.append(form);
      const oportunidad = document.createElement('select');
      for (const [v, t] of [['SI','Sí'],['NO','No hubo oportunidad']] as const) { const o=document.createElement('option');o.value=v;o.textContent=t;oportunidad.append(o); }
      const noQuiso=document.createElement('input');noQuiso.type='checkbox'; const nivel=document.createElement('select');
      const n0=document.createElement('option');n0.value='';n0.textContent='Elige nivel';nivel.append(n0);
      ((datos.escala as {niveles?:Array<{codigo:string;nombre:string}>}).niveles??[]).forEach(n=>{const o=document.createElement('option');o.value=n.codigo;o.textContent=n.nombre;nivel.append(o);});
      const motivo=document.createElement('select');
      for(const v of ['NORMA_DEL_HOGAR','FALTAN_RECURSOS','CUIDADOR_CON_OTRO_RESIDENTE','NO_ESTABA_EN_EL_HOGAR','ENFERMO_O_INDISPUESTO','OTRO']){const o=document.createElement('option');o.value=v;o.textContent=v;motivo.append(o);}
      const otro=document.createElement('input');otro.placeholder='Describe el motivo'; const nota=document.createElement('input');nota.placeholder='Nota opcional';
      const pasos=actividad.plantilla.pasos.map(p=>{const l=document.createElement('label');const c=document.createElement('input');c.type='checkbox';c.checked=anterior?.pasos.find(x=>x.orden===p.orden)?.cumplido??false;l.append(c,p.descripcion);form.append(l);return c;});
      const secuencia=document.createElement('select'); for(const [v,t] of [['true','Correcta'],['false','Incorrecta']] as const){const o=document.createElement('option');o.value=v;o.textContent=t;secuencia.append(o);} secuencia.value=String(anterior?.secuenciaCorrecta??true);
      const sync=()=>{motivo.hidden=oportunidad.value!=='NO';otro.hidden=oportunidad.value!=='NO'||motivo.value!=='OTRO';noQuiso.disabled=oportunidad.value!=='SI';nivel.hidden=oportunidad.value!=='SI'||noQuiso.checked;pasos.forEach(p=>p.disabled=oportunidad.value!=='SI'||noQuiso.checked);secuencia.hidden=!actividad.secuenciaRelevante||oportunidad.value!=='SI'||noQuiso.checked; if(oportunidad.value==='SI') otro.value='';};
      [oportunidad,noQuiso,motivo].forEach(x=>x.addEventListener('change',sync)); form.append(oportunidad,motivo,otro,noQuiso,nivel,secuencia,nota);sync();
      const guardar=document.createElement('button');guardar.textContent='Confirmar';form.append(guardar);const cancelar=document.createElement('button');cancelar.type='button';cancelar.textContent='Cancelar';form.append(cancelar);
      const avanzar=()=>{indice++;siguiente();}; cancelar.onclick=avanzar;
      form.onsubmit=(e)=>{e.preventDefault();const registro:RegistroDatos=construirRegistro({codigo:crypto.randomUUID(),expediente,actividad:actividad.codigo,versionPlantilla:actividad.plantilla.version,pasos:actividad.plantilla.pasos.map((p,i)=>({orden:p.orden,cumplido:pasos[i].checked})),oportunidad:oportunidad.value==='SI',motivoSinOportunidad:oportunidad.value==='NO'?motivo.value as RegistroDatos['motivoSinOportunidad']:undefined,motivoOtro:otro.value,noQuiso:noQuiso.checked,nota:nota.value,nivel:nivel.value,secuenciaCorrecta:secuencia.value==='true',secuenciaRelevante:actividad.secuenciaRelevante},anterior,turno,inicio,new Date());try{validarRegistro(registro);void almacen.guardarRegistro(registro).then(()=>{void procesarPendientes();avanzar();});}catch(err){form.append(document.createTextNode(err instanceof Error?err.message:'No se pudo guardar.'));}};
    }; siguiente();
  };
}
function prioridad(datos: DatosTurnoLocal, codigo: string): number { return datos.actividadesResidente.some((a) => a.actividad === codigo && a.estado === 'PRIORITARIA') ? 1 : 0; }
